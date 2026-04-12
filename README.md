# Habit Tracker API

[![CI](https://github.com/jaejinu/jaejinu_project004_habit_tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/jaejinu/jaejinu_project004_habit_tracker/actions/workflows/ci.yml)

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

# 2. 앱 실행
./gradlew bootRun

# 3. 테스트 (단위 + Testcontainers Postgres 통합)
./gradlew test
```

> 테스트에는 Docker 데몬이 필요합니다 (Testcontainers Postgres 16).

실행 후 확인:

- Swagger UI: http://localhost:8080/swagger-ui.html
- REST Docs HTML: http://localhost:8080/docs/index.html (Spring REST Docs 스냅샷)
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
- [x] **7단계** — 빌드/테스트 검증 + CI
  - Gradle wrapper 8.10.1 생성, `./gradlew test` 전체 통과 (9 tests)
  - `@EnableJpaAuditing`을 `infra/persistence/JpaConfig`로 분리 → `@WebMvcTest` 슬라이스 호환
  - PostgreSQL 네이티브 쿼리에 명시적 `CAST(:today AS DATE)` — JDBC 파라미터 타입 추론 오류 방지
  - Testcontainers Postgres 16 기반 통합 테스트 `CheckInRepositoryIntegrationTest` — LAG 스트릭 쿼리 7 케이스
  - GitHub Actions CI (`.github/workflows/ci.yml`): Java 21 + Gradle + Testcontainers + Docker 이미지 빌드 (main만)
- [x] **5단계** — 문서화 (OpenAPI + Markdown 가이드 + Spring REST Docs)
  - 컨트롤러 전체에 `@Tag/@Operation/@Parameter/@ApiResponse` 어노테이션 — Swagger UI 풍부화
  - `OpenApiConfig`: 서버 목록 + `bearerAuth` (JWT) 시큐리티 스키마 전역 적용
  - `docs/api-reference.md` — 엔드포인트 레퍼런스
  - `docs/badge-guide.md` — README 삽입 5분 퀵스타트
  - `examples/readme-example.md` — 복붙 가능한 샘플 README
  - Spring REST Docs asciidoctor 파이프라인: `Auth`/`Habit`/`CheckIn`/`Badge` 스냅샷 테스트, `bootJar` 가 `/docs/index.html` 임베드 (`src/docs/asciidoc/index.adoc`)
  - CI가 `api-reference-html` 아티팩트로 생성 HTML 업로드
  - 설계 문서: `docs/design/step5-docs.md`
- [x] **8단계** — 운영 자동화 (Dependabot + `.gitattributes`)
  - `.github/dependabot.yml` — 주간 Gradle/Actions/Docker 업데이트, Spring/Testing 그룹핑
  - 1차 반영: jjwt 0.12→0.13, testcontainers 1.20→1.21, actions/checkout v4→v6, upload-artifact v4→v7, gradle/actions v4→v6
  - `.gitattributes` — `gradlew` LF + exec bit 보존, 텍스트 파일 eol=lf 강제
  - `@ConfigurationPropertiesScan` 으로 `JwtProperties`/`RateLimitProperties` 표준화

## 남은 작업 (Backlog)

우선순위 순. GitHub Issues 로 트래킹 중이며 PR 으로 각각 진행 가능.

1. **Spring Boot 4.0.5 마이그레이션** (보류 PR [#4](https://github.com/jaejinu/jaejinu_project004_habit_tracker/pull/4))
   - 메이저 버전 점프: Spring Framework 7 baseline, Jackson 3.1, `@MockBean` deprecation 등.
   - 테스트 슬라이스 전체 재검증 필요.

2. **Gradle 9.4.1 업그레이드** (보류 PR [#7](https://github.com/jaejinu/jaejinu_project004_habit_tracker/pull/7))
   - `org.asciidoctor.jvm.convert` / `spring-boot` 플러그인 호환성 확인 후 머지.

3. **프로덕션 시크릿 주입 전략 결정** ([#9](https://github.com/jaejinu/jaejinu_project004_habit_tracker/issues/9))
   - `application-prod.yml` 의 `JWT_SECRET`, `DB_PASSWORD`, `REDIS_PASSWORD` 가 현재는 플레인 `${...}` 환경변수.
   - AWS SecretsManager / GitHub OIDC / Doppler 등 선택 필요.

4. **뱃지 스크린샷 작성** ([#10](https://github.com/jaejinu/jaejinu_project004_habit_tracker/issues/10))
   - `docs/badge-guide.md` 의 `<!-- TODO: insert screenshots -->` 자리에 light/dark × {calendar, streak, total} 스크린샷 삽입.
   - 앱을 띄워 실제 Chrome 캡처 필요.

5. **REST Docs 스냅샷 확장** ([#11](https://github.com/jaejinu/jaejinu_project004_habit_tracker/issues/11))
   - 현재 커버: Auth signup/login, Habit list/create/delete, CheckIn create, Badge calendar ×2 (200+304).
   - 미커버: Habit get/update/visibility, CheckIn list/patch, Public habits/stats, SharedBadge issue.
   - 패턴은 기존 `*DocsTest` 참고. 기존 테스트의 주의점 네 가지는 `docs/RESUME.md` 와 `memory/test_and_ci.md` 참조.

6. **Testcontainers 재사용 캐싱** — 현재 로컬은 `.withReuse(true)` 지만 CI는 매번 `postgres:16-alpine` 풀. `setup-gradle` 캐시로 완화되지만, 필요 시 경량 이미지(`gvenzl/oci-postgres`) 전환 고려.

> 세션 단위로 이어서 작업할 때는 `docs/RESUME.md` 를 먼저 확인하세요 — 현재 상태 / 빠른 재개 명령 / 세션 간 유지할 gotcha 목록이 들어 있습니다.

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
