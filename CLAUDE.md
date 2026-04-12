# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

Habit Tracker — public REST API. Users log daily habit check-ins; we expose embeddable SVG badges (53×7 GitHub-grass calendar, streak/total shields) so they can be pasted into external READMEs. Public, unauthenticated, CDN-friendly badge endpoints are a first-class concern.

## Stack

- Spring Boot 3.3.4 / Java 21 (Temurin)
- Gradle **Groovy DSL** (not Kotlin)
- Spring Data JPA + Querydsl 5.1.0 (jakarta classifier)
- PostgreSQL 16 (prod), H2 (tests)
- Redis 7 (streak cache, SVG cache, rate-limit, dedup)
- JWT auth (jjwt 0.12.6)
- springdoc-openapi 2.6.0
- Prometheus + Grafana via docker-compose

## Run

```bash
docker compose up -d                      # Postgres, Redis, Prometheus, Grafana
gradle wrapper --gradle-version 8.10      # first time only (or IntelliJ sync)
./gradlew bootRun                         # app on :8080
./gradlew test                            # unit + integration
```

- Swagger: http://localhost:8080/swagger-ui.html
- Actuator: http://localhost:8080/actuator/health
- Prometheus scrape path: `/actuator/prometheus`

## Package Layout

```
com.habit
├── HabitTrackerApplication.java
├── common/             # BaseTimeEntity, exceptions, error codes
├── domain/
│   ├── user/           # User entity, repository
│   ├── habit/          # Habit, CheckIn, Streak + services
│   ├── stats/          # HabitStats (daily pre-aggregated)
│   └── badge/          # SharedBadge, SVG generators, PDF report
├── infra/
│   ├── cache/          # RedisConfig, BadgeCacheService
│   ├── scheduler/      # Daily streak/stats jobs
│   └── security/       # JWT filter, SecurityConfig
└── api/
    ├── HabitController.java
    ├── CheckInController.java
    ├── BadgeController.java      # SVG responses, Cache-Control headers
    └── PublicController.java     # unauthenticated public endpoints
```

Rule of thumb: **domain** holds entities + repositories + domain services. **infra** is Spring-coupled adapters (Redis, schedulers, security). **api** is HTTP.

## Conventions

- **Entities**: protected no-arg constructor, `@Builder` static factory, explicit `@Table` name, snake_case columns. Use `BaseTimeEntity` for `created_at` / `updated_at` auditing.
- **IDs**: `Long id` with `IDENTITY` generation. Public-facing IDs use separate UUID fields (e.g., `User.publicId`, `SharedBadge.publicToken`) — never expose internal PKs in URLs.
- **Dates**: store `LocalDate` for day-level check-ins (column `date`). Store `Instant` / `LocalDateTime` in UTC for timestamps. JVM and DB run in UTC; timezone conversion happens at the HTTP edge.
- **Transactions**: default `@Transactional(readOnly = true)` at service class level; override per write method.
- **Errors**: throw `BusinessException(ErrorCode)`. Controller layer never throws raw exceptions; `GlobalExceptionHandler` maps them to consistent `ErrorResponse` shape.
- **Query style**: simple finders via Spring Data method names; complex read models via Querydsl (`@RequiredArgsConstructor`-injected `JPAQueryFactory`). Native SQL (`@Query(value=..., nativeQuery=true)`) only where necessary (e.g., PostgreSQL `LAG` window fn for streak recalc) — guard such tests with `@ActiveProfiles("integration")` or Testcontainers, not H2.
- **Cache keys**: prefix by domain — `habit:checkin:{userId}:{habitId}:{date}`, `badge:svg:{token}:{type}:{params-hash}`, `rate:ip:{ip}:{hourBucket}`.
- **Public endpoints**: every `/api/v1/public/**` and `/api/v1/badges/**` is a public contract. Breaking changes require a new `/v2`. Always set `Cache-Control` and `ETag` on SVG responses.

## Streak Calculation

Two paths, used together:

1. **Incremental** (hot path, in `CheckInService.checkIn`):
   - On new check-in, look up `Streak.lastCheckedDate`. If it's yesterday (user-local), increment; else reset to 1. Update `longestStreak`. This is O(1).
2. **Recalc** (daily job + "fix drift" endpoint):
   - Native PostgreSQL query using `LAG()` window function over `check_ins` for a habit to rebuild the true streak. Used by the scheduler to correct drift and handle backfilled check-ins.

Redis holds a short-lived dedup key (`habit:checkin:{...}:{date}` with ~2m TTL) to short-circuit repeated POSTs before hitting the DB unique constraint. The DB unique constraint on `(habit_id, checked_date)` is the source of truth.

## Testing

- Unit tests for pure logic (streak math, SVG string generation).
- Integration tests for JPA + native queries use **Testcontainers Postgres** (already on the test classpath). Do not rely on H2 for LAG-based queries.
- Spring REST Docs for API documentation — assert response structure in tests, snippets get assembled into `docs/`.

## Public Badge Endpoints

- Routes live under `/api/v1/badges/**` and are serviced by `BadgeController` → `PublicBadgeService`.
- SVG body is cached in Redis via `BadgeCacheService` (`badge:svg:{token}:{type}:{sha1-6-of-sorted-params}`, TTL 1h, SETNX-based stampede guard).
- Every SVG response sets: `Content-Type: image/svg+xml;charset=utf-8`, `Cache-Control: public, max-age=3600, s-maxage=3600, stale-while-revalidate=600`, `ETag` (SHA-1 first 16 hex chars of body), `Vary: Accept-Encoding`. If `If-None-Match` matches ETag → respond 304 with the same headers but no body.
- These routes are **rate-limit exempt** (CDN fronted per project spec). Don't add rate-limit checks to them.

## Scheduled Jobs (`infra/scheduler/`)

All jobs: `@ConditionalOnProperty(name = "app.scheduler.enabled", matchIfMissing = true)` — tests can disable via `app.scheduler.enabled=false`. All `@Scheduled` cron uses `zone = "UTC"`. All use `LocalDate.now(ZoneOffset.UTC)` — never default zone.

| Job | Cron | What it does |
|---|---|---|
| `StreakMaintenanceJob` | `0 10 0 * * *` | Resets `currentStreak` to 0 for streaks where `lastCheckedDate < today - 1`. Preserves `longestStreak`, `totalDays`, `lastCheckedDate`. Emits `habit.streak.broken.total`. |
| `DailyStatsAggregationJob` | `0 5 2 * * *` | For each active habit, upserts `HabitStats` for yesterday. Idempotent via `HabitStatsService.recordFor` (checks existence first). Wrapped in `habit.stats.aggregation.duration` Timer, per-row `habit.stats.aggregation.rows` Counter. |
| `WeeklyStreakRecalcJob` | `0 0 3 * * SUN` | LAG-based recalc via `StreakService.recalculate(habitId, today)` for every active habit. Per-habit try/catch so one bad row doesn't abort the run. No class-level `@Transactional` — delegates to the service. |

When adding a new job: mirror this pattern (UTC cron+zone, `ConditionalOnProperty`, per-item try/catch for batch jobs). Iteration currently uses `findAll`-style queries — paginate when habit count > 10k (TODO markers are in the code).

## Metrics (Micrometer + Prometheus)

Scrape endpoint: `/actuator/prometheus`.

| Metric | Type | Tags | Where |
|---|---|---|---|
| `habit.checkin.total` | Counter | — | `CheckInService.checkIn` on success |
| `habit.streak.broken.total` | Counter | — | `StreakMaintenanceJob` per reset |
| `habit.stats.aggregation.duration` | Timer | — | `DailyStatsAggregationJob` whole-run |
| `habit.stats.aggregation.rows` | Counter | — | `DailyStatsAggregationJob` per insert |
| `badge.svg.cache.hit.total` | Counter | `type` | `BadgeCacheService.computeIfAbsent` |
| `badge.svg.cache.miss.total` | Counter | `type` | `BadgeCacheService.computeIfAbsent` |
| `badge.svg.generation.duration` | Timer | `type` | `BadgeCacheService.computeIfAbsent` around generator |

Metric names use dots (`.`) — Prometheus registry converts to underscores (`_`) automatically. Always pass a closed-set tag value (no user input) to avoid metric cardinality explosions.

## Auth (`infra/security/`)

- JWT, HS256 via `io.jsonwebtoken:jjwt`. Secret from `app.security.jwt.secret`, TTL `app.security.jwt.access-token-ttl` (default 1h).
- `JwtService` issues and parses tokens; expired → `AUTH_TOKEN_EXPIRED` (401), invalid signature/format → `AUTH_TOKEN_INVALID` (401).
- `JwtAuthenticationFilter` reads `Authorization: Bearer <token>`, sets `SecurityContextHolder` with a `UsernamePasswordAuthenticationToken` where `principal = Long userId`.
- `SecurityBeans` exposes `BCryptPasswordEncoder`.
- `AuthController` at `/api/v1/auth/**` exposes `signup` / `login`. Signup stores BCrypt hash; login verifies + issues `{accessToken, tokenType: "Bearer", expiresIn}`.
- `@AuthenticationPrincipal Long userId` in controllers — only works because we set the principal to a raw `Long`. If you change the principal shape, update every `@AuthenticationPrincipal` site.

Public path allow-list (in `SecurityConfig.securityFilterChain`):
- `/api/v1/auth/**`, `/api/v1/public/**`, `/api/v1/badges/**`
- `/actuator/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`

Everything else is `authenticated()`. Filter order:
```
JwtAuthenticationFilter → RateLimitFilter → UsernamePasswordAuthenticationFilter (unused)
```

## Rate Limit (`infra/security/RateLimitFilter`)

- Fixed-bucket per-hour counter: `INCR rate:{scope}:{id}:{yyyyMMddHH}` then `EXPIRE` only when the counter was just created (count == 1). Scope is `user` when the JWT authentication principal resolves to `Long`, else `ip` (X-Forwarded-For first entry, else remoteAddr).
- Limits: 60/h anon, 1000/h authenticated (configurable via `app.rate-limit.*`).
- Exempt path prefixes: `/api/v1/badges/`, `/actuator/`, `/swagger-ui`, `/v3/api-docs`.
- On exceed: HTTP 429 with `ErrorResponse` body (`COMMON_429`) + `Retry-After: <seconds-until-next-hour>`.

## Documentation

- **Swagger UI**: `/swagger-ui.html` (or `/swagger-ui/index.html`). Configured by `infra/docs/OpenApiConfig` with a global `bearerAuth` (JWT) scheme. Public endpoints opt out via `@SecurityRequirements({})` at the method level.
- **Markdown docs** live under `docs/`:
  - `docs/api-reference.md` — endpoint reference.
  - `docs/badge-guide.md` — user-facing README-embed guide.
  - `docs/design/step*.md` — per-step architecture specs (the ultra-plans).
  - `examples/readme-example.md` — copy-pasteable sample README.
- **Spring REST Docs asciidoc generation is deferred.** See `docs/design/step5-docs.md` Surface 3 for the rationale. One sample MockMvc test (`BadgeControllerDocsTest`) shows the pattern for future snapshot docs — wire up the asciidoctor plugin only when test coverage grows past ~20 endpoints.

When adding a new controller method: (1) class gets `@Tag`, (2) method gets `@Operation`, (3) meaningful non-2xx cases get `@ApiResponse`, (4) non-obvious path/query params get `@Parameter(description, example)`, (5) if the endpoint is unauthenticated add `@SecurityRequirements({})`.

## Things NOT to do

- Don't use Kotlin DSL (`build.gradle.kts`). Keep Groovy.
- Don't expose internal `Long id` values in public JSON or URLs — use `publicId`/`publicToken`.
- Don't write streak logic that round-trips through server local time — always pass the user's zoned day explicitly.
- Don't mock the Postgres in tests that exercise `LAG()` or date arithmetic — use Testcontainers.
- Don't add rate-limit checks to `/api/v1/badges/**` — CDN handles that layer.
- Don't call `@Modifying` repository methods from inside a `@Transactional(readOnly = true)` service — either use method-level `@Transactional` override, or extract to a separate bean (self-invocation through `this.` does NOT re-enter the Spring proxy, so nested `@Transactional` annotations are ignored).
