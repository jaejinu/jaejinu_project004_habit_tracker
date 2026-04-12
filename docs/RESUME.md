# 다음에 이어서 진행할 항목

세션을 중단한 시점 기준 정리입니다. 다시 시작할 때 이 파일 먼저 읽으세요.

## 현재 상태 (로컬 빌드 OK)

- `./gradlew test` → **15 tests passing** (Auth/Habit/CheckIn/Badge docs + 7 Postgres LAG 통합 테스트)
- `./gradlew bootJar` → 성공. `/docs/index.html` 에 Auth/Habit/CheckIn/Badge 모든 스니펫 포함됨.
- 최신 원격 main: `6038b61` (REST Docs 확장 커밋). 직전 CI 실행 결과 확인 필요.

## 이번 세션에서 완료한 것

- CI gradlew exec bit 수정, `-x test` on bootBuildImage 제거 → docker-image 잡 정상화
- `@ConfigurationPropertiesScan` 로 RateLimitProperties/JwtProperties 전환 (경고 제거)
- `.github/dependabot.yml` 추가 (weekly: gradle + actions + docker)
- `.gitattributes` — gradlew LF + exec bit 고정
- Dependabot PR 6건 머지 (actions v4→v6/v7, testing 1.20.2→1.21.4, jjwt 0.12→0.13)
- Dependabot PR 2건 보류 코멘트:
  - **#4** — Spring Boot 3.3.4 → **4.0.5** (major). 마이그레이션 계획 잡고 수동 처리.
  - **#7** — Gradle 8.10.1 → **9.4.1** (major). 플러그인 호환성 확인 후 수동 처리.
- REST Docs 확장: `AuthControllerDocsTest`, `HabitControllerDocsTest`, `CheckInControllerDocsTest` 추가.
  `src/docs/asciidoc/index.adoc` 확장 → `/docs/index.html` 전체 API 커버.

## 다음에 해야 할 작업

### 즉시 처리 가능
1. **CI 녹색 확인** — `gh run list --workflow=ci.yml --branch=main --limit 3`.
   직전 커밋(`6038b61`)이 완전히 녹색이면 이 섹션 지워도 됨.

2. **REST Docs 스냅샷 추가 (선택)**
   - `HabitController` 의 `GET /{habitId}`, `PATCH /{habitId}`, `PATCH /{habitId}/visibility` 아직 미커버.
   - `CheckInController` 의 `GET`(list), `PATCH /{date}` 미커버.
   - `PublicController`, `SharedBadgeController` 미커버.
   - 패턴은 기존 `HabitControllerDocsTest` / `CheckInControllerDocsTest` 참고.
   - 신규 섹션 추가시 `src/docs/asciidoc/index.adoc` 업데이트 잊지 말 것.

### 중장기 계획
3. **스크린샷** — `docs/badge-guide.md` 의 `<!-- TODO: insert screenshots -->` 플레이스홀더. 실제 앱 기동 후 light/dark 테마 뱃지 캡처 필요.

4. **Spring Boot 4 migration (보류 PR #4)**
   - Spring Framework 7 baseline.
   - `@MockBean` deprecated → `@MockitoBean` (org.springframework.test.context.bean.override).
   - Jackson 3.1 전환.
   - `spring.autoconfigure.exclude` 문법 일부 변경.
   - 머지하기 전에 테스트 슬라이스 전부 점검.

5. **Gradle 9 migration (보류 PR #7)**
   - `org.asciidoctor.jvm.convert` 플러그인 9 호환성 확인.
   - `spring-boot` 플러그인 공식 지원 버전 확인.
   - Deprecation warning 정리.

6. **프로덕션 시크릿 주입** — `application-prod.yml` 의 `JWT_SECRET`, `DB_PASSWORD`, `REDIS_PASSWORD` 는 플레인 환경변수. AWS SecretsManager / GitHub OIDC / Doppler 등 선택 필요.

7. **Testcontainers 이미지 캐싱 최적화** — CI가 매 실행마다 postgres:16-alpine 풀. `setup-gradle@v6` 캐시가 자동으로 잡아주지만 `gvenzl/oci-postgres` 같은 작은 이미지 전환도 고려 가능.

## 알려진 주의사항 (세션 간 유지)

- `RateLimitFilter` 의 `SecurityContextHolder` principal 가정은 `Long userId`. 변경 시 전 사이트 일괄 수정 (메모리 `auth_and_docs.md`).
- 네이티브 쿼리에서 `LocalDate` 파라미터는 항상 `CAST(:param AS DATE)` 로 감싸야 Postgres JDBC 타입 추론 이슈 없음.
- `@EnableJpaAuditing` 은 `infra/persistence/JpaConfig` 에만 존재. `@SpringBootApplication` 에 다시 붙이면 `@WebMvcTest` 가 깨짐.
- `@WebMvcTest` 슬라이스에서 `@AuthenticationPrincipal Long userId` 는 `null` 로 resolve됨 (SecurityAutoConfiguration 제외 때문). 이때 Mockito 매처는 `anyLong()` 대신 `any()` 사용해야 함 — `any()` 만 null 매치.
- `pathParameters(...)` 스니펫을 쓸 때는 `MockMvcRequestBuilders` 대신 `RestDocumentationRequestBuilders` 의 `get/post/delete` 를 임포트해야 URL 템플릿이 추출됨.
- Jackson 이 null 필드를 스킵(`default-property-inclusion=non_null`)하므로 nullable 응답 필드는 `.type(JsonFieldType.X)` 명시 필요.

## 빠른 재개 명령

```bash
cd /c/Users/User/Documents/jaejinu_project/jaejinu_project004_habit_tracker
export JAVA_HOME="/c/Users/User/.jdks/temurin-21.0.6"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew test bootJar        # 전체 빌드 + 테스트
./gradlew asciidoctor         # REST Docs HTML 만

git status
gh run list --workflow=ci.yml --branch=main --limit 3
gh pr list --limit 10
```

## 선행 단계 레퍼런스

- Ultra-plan 설계 문서: `docs/design/step*.md` (step 1 → step 6 순).
- 메모리: `C:/Users/User/.claude/projects/.../memory/*.md` — project_overview, tech_stack, architecture_conventions, rate_limit_and_caching, scheduler_and_metrics, auth_and_docs, test_and_ci, resume_pointer.
