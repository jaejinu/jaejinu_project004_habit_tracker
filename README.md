# Habit Tracker API

GitHub 잔디 스타일 습관 트래커 공개 API.
매일 체크인하면 53주 × 7일 SVG 캘린더와 스트릭 뱃지를 외부 README에 삽입할 수 있습니다.

## Stack

- Spring Boot 3.3.x / Java 21
- Spring Data JPA + Querydsl
- PostgreSQL 16 / Redis 7
- Spring Security + JWT
- Prometheus + Grafana
- springdoc-openapi (Swagger UI)

## Quick Start

```bash
# 1. 인프라 컨테이너 기동 (Postgres, Redis, Prometheus, Grafana)
docker compose up -d

# 2. Gradle wrapper 생성 (최초 1회 - 시스템에 gradle 설치 필요 or IntelliJ에서 Sync)
gradle wrapper --gradle-version 8.10

# 3. 앱 실행
./gradlew bootRun
```

실행 후 확인:

- Swagger UI: http://localhost:8080/swagger-ui.html
- Actuator Health: http://localhost:8080/actuator/health
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

## Profiles

- `local` (기본): 로컬 Postgres/Redis 연결
- `prod`: 환경 변수 기반 (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `JWT_SECRET`)

## Directory Layout

```
src/main/java/com/habit/
├── common/      # BaseTimeEntity, ErrorCode, BusinessException, GlobalExceptionHandler
├── domain/
│   ├── user/    # User, UserRepository
│   ├── habit/   # Habit, CheckIn, Streak + HabitService, CheckInService, StreakService
│   ├── stats/   # HabitStats (daily aggregate)
│   └── badge/   # SharedBadge (public tokens for badge embedding)
├── infra/
│   ├── cache/   # RedisConfig (StringRedisTemplate + CacheManager)
│   ├── scheduler/
│   └── security/
└── api/         # 컨트롤러 (habit, check-in, badge, public)
```

## 진행 상황

- [x] **1단계** — 프로젝트 초기 세팅 (Gradle/Docker/Prometheus/application.yml)
- [x] **2단계** — 도메인 엔티티 + 체크인/스트릭 서비스
  - 엔티티: `User`, `Habit`, `CheckIn`, `Streak`, `HabitStats`, `SharedBadge` (+ enums)
  - `CheckInService`: Redis `SETNX` 중복 방지(2분 TTL) → DB 유니크 제약으로 최종 검증 → 스트릭 증가
  - `StreakService`: PostgreSQL `LAG` 기반 native query로 드리프트 보정 재계산
  - 설계 문서: `docs/design/step2-entities.md`
- [x] **3단계** — 공개 Badge API + SVG 생성 + Rate Limit
  - SVG 생성기: `CalendarSvgGenerator` (53×7 잔디), `StreakBadgeGenerator`/`TotalBadgeGenerator` (shields.io 스타일)
  - `BadgeCacheService`: Redis TTL 1h + `SETNX` 기반 stampede guard
  - `PublicBadgeService`: 토큰→공개 습관 검증→캐시 or 생성, 조회수 증가
  - `RateLimitFilter`: Redis 시간당 고정 버킷 (`INCR`+conditional `EXPIRE`), 익명 60/h, 인증 1000/h
  - 공개 API:
    - `GET /api/v1/badges/{token}/{calendar|streak|total}.svg` — rate-limit 면제 + `Cache-Control: max-age=3600` + `ETag` 304
    - `GET /api/v1/public/{publicId}/habits` / `/stats`
    - `POST /api/v1/habits/{habitId}/badges` (인증 필요)
  - 설계 문서: `docs/design/step3-badges.md`
- [x] **3b단계** — JWT 인증 (X-User-Id 스텁 교체)
  - `POST /api/v1/auth/signup`, `POST /api/v1/auth/login` — BCrypt + HS256 JWT
  - `JwtAuthenticationFilter` → `RateLimitFilter` 순서로 체인
  - `RateLimitFilter`가 `SecurityContextHolder`에서 userId 조회 → 인증 사용자 1000/h 버킷 자동 적용
  - `SharedBadgeController`는 `@AuthenticationPrincipal Long userId` 사용
  - 공개 경로: `/api/v1/auth/**`, `/api/v1/public/**`, `/api/v1/badges/**`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`
  - 설계 문서: `docs/design/step3b-auth.md`
- [x] **4단계** — Spring Scheduler 일별 통계 집계 + 스트릭 드리프트 보정 + Micrometer 메트릭
  - `StreakMaintenanceJob` 매일 00:10 UTC — `lastCheckedDate < today-1` 인 스트릭 `currentStreak=0` 리셋
  - `DailyStatsAggregationJob` 매일 02:05 UTC — 어제 날짜로 `HabitStats` upsert (idempotent)
  - `WeeklyStreakRecalcJob` 일요일 03:00 UTC — 전체 활성 습관 LAG 재계산 (드리프트 보정)
  - 메트릭: `habit.checkin.total`, `habit.streak.broken.total`, `habit.stats.aggregation.{duration,rows}`, `badge.svg.cache.{hit,miss}.total{type}`, `badge.svg.generation.duration{type}`
  - 스케줄러 토글: `app.scheduler.enabled` (기본 true, 테스트에서 false 설정 가능)
  - 설계 문서: `docs/design/step4-scheduler.md`
- [x] **6단계** — Habit CRUD + Check-In 컨트롤러 완성
  - `HabitController`: `GET /api/v1/habits`, `GET/PATCH/DELETE /{habitId}`, `POST`, `PATCH /{habitId}/visibility`
  - `CheckInController`: `POST /api/v1/habits/{habitId}/check-ins`, `GET` (기본 윈도우 `[today-29, today]` UTC), `PATCH /{date}`
  - `HabitService.delete`: cascade (badges → stats → check-ins → streak → habit) 단일 트랜잭션
  - `CheckInRepository`/`HabitStatsRepository`/`SharedBadgeRepository`에 `@Modifying deleteAllByHabitId` 추가
  - 설계 문서: `docs/design/step6-crud.md`
- [x] **5단계** — 문서화 (OpenAPI + Markdown 가이드)
  - 컨트롤러 전체에 `@Tag/@Operation/@Parameter/@ApiResponse` 어노테이션 — Swagger UI 풍부화
  - `OpenApiConfig`: 서버 목록 + `bearerAuth` (JWT) 시큐리티 스키마 전역 적용
  - `docs/api-reference.md` — 엔드포인트 레퍼런스
  - `docs/badge-guide.md` — README 삽입 5분 퀵스타트
  - `examples/readme-example.md` — 복붙 가능한 샘플 README
  - 샘플 테스트 `BadgeControllerDocsTest` (full Spring REST Docs asciidoc 은 follow-up)
  - 설계 문서: `docs/design/step5-docs.md`

## 사용 예시 — README 뱃지 삽입

```markdown
![잔디](https://api.habittracker.io/api/v1/badges/<token>/calendar.svg?theme=dark)
![연속](https://api.habittracker.io/api/v1/badges/<token>/streak.svg?lang=ko)
![총합](https://api.habittracker.io/api/v1/badges/<token>/total.svg?lang=ko)
```

쿼리 파라미터: `color=RRGGBB` (잔디 색), `theme=light|dark`, `lang=en|ko`, `width=N`.

## Rate Limit

| 대상 | 한도 |
|---|---|
| 익명 IP | 60 req/h |
| 인증 사용자 (JWT Bearer) | 1000 req/h |
| SVG 뱃지 경로 (`/api/v1/badges/**`) | 면제 (CDN 캐시) |
| `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**` | 면제 |

초과 시 `HTTP 429` + `Retry-After` 헤더(다음 시간 버킷까지의 초).

## 설계 원칙 (상세는 `CLAUDE.md`)

- 엔티티는 애그리거트 경계 패턴 — FK를 `@ManyToOne` 대신 `Long`으로 저장.
- 공개 식별자는 항상 별도 UUID/토큰 컬럼 (`User.publicId`, `SharedBadge.publicToken`).
- 스트릭은 핫패스(증분) + 배치(재계산) 이중 경로. 중복 체크인은 Redis 선점 + DB 유니크 제약 이중 방어.
- Native SQL(`LAG`)은 Testcontainers Postgres에서만 테스트 — H2 금지.
