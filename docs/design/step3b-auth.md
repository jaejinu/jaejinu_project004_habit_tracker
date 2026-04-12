# Step 3b — JWT Authentication (Ultra-Plan)

Replaces the `X-User-Id` stub introduced in step 3.

## Scope

- Email + password signup/login → issue JWT access token (no refresh token in this step; can be added later).
- Signed with HS256 using `app.security.jwt.secret`.
- All API routes outside the public/allow-listed paths require a valid `Authorization: Bearer <token>` header.
- `RateLimitFilter` stops reading `X-User-Id`; reads userId from `SecurityContextHolder`.
- `SharedBadgeController.issue(...)` takes `@AuthenticationPrincipal Long userId` instead of the header.

## Token Shape

- Access token JWT, HS256.
- Claims: `sub = userId` (as string), `iat`, `exp` (= iat + `app.security.jwt.access-token-ttl`, default `PT1H`).
- No roles/authorities in v1. If we later add admin, add a `roles` claim.

## Endpoints

All under `/api/v1/auth/**`, all `permitAll`.

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/v1/auth/signup` | `{email, nickname, password}` | `{userId, publicId, email, nickname}` |
| POST | `/api/v1/auth/login` | `{email, password}` | `{accessToken, tokenType: "Bearer", expiresIn: seconds}` |

Password rules (MVP): ≥ 8 chars. Email basic format. Stored as BCrypt (strength 10).

## Error Codes (add to `ErrorCode`)

- `AUTH_INVALID_CREDENTIALS` (401, `AUTH_401_CRED`, "이메일 또는 비밀번호가 올바르지 않습니다.")
- `AUTH_TOKEN_INVALID` (401, `AUTH_401_TOKEN`, "토큰이 유효하지 않습니다.")
- `AUTH_TOKEN_EXPIRED` (401, `AUTH_401_EXPIRED`, "토큰이 만료되었습니다.")

## Filter Chain Order

```
JwtAuthenticationFilter  →  RateLimitFilter  →  (standard Spring Security chain)
```

Both are `OncePerRequestFilter`. JWT filter runs first so authenticated principal is available when the rate-limit filter decides the per-hour bucket scope (user vs ip).

## Request Flow

1. `JwtAuthenticationFilter` reads `Authorization` header. If missing or not `Bearer `, pass through (anonymous).
2. If present, parse via `JwtService.parseUserId`. On failure throw `BusinessException(AUTH_TOKEN_INVALID)` or `AUTH_TOKEN_EXPIRED` (distinguish via jjwt's `ExpiredJwtException`). The filter writes an `ErrorResponse` JSON via `HandlerExceptionResolver`-style mechanism — simplest: catch in filter, write JSON directly (mirrors `RateLimitFilter` 429 behavior).
3. On success, build `UsernamePasswordAuthenticationToken(userId, null, List.of())` and set on `SecurityContextHolder`.
4. `RateLimitFilter` reads principal from `SecurityContextHolder`; if non-null Long → scope=`user`; else ip.

## Entity Changes

`User`:
- Add `passwordHash String` nullable=false, length 100 (BCrypt output is 60 chars; 100 gives headroom).
- New static factory `User.createWithPassword(String email, String nickname, String passwordHash)` that sets `publicId = UUID.randomUUID()`.

Schema migration: Hibernate `ddl-auto=update` on local will add the column as nullable by default. For a fresh DB this is fine. We annotate with `nullable = false` for production, so existing rows would error — but there are none. Acceptable for dev.

## File Inventory

New:
- `src/main/java/com/habit/infra/security/JwtProperties.java`
- `src/main/java/com/habit/infra/security/JwtService.java`
- `src/main/java/com/habit/infra/security/JwtAuthenticationFilter.java`
- `src/main/java/com/habit/infra/security/SecurityBeans.java` — `PasswordEncoder` bean
- `src/main/java/com/habit/domain/auth/AuthService.java`
- `src/main/java/com/habit/api/AuthController.java`
- `src/main/java/com/habit/api/AuthDtos.java` (signup/login requests + responses)

Modified:
- `com.habit.common.exception.ErrorCode` — add 3 auth codes
- `com.habit.domain.user.User` — add passwordHash, new factory
- `com.habit.infra.security.SecurityConfig` — add JWT filter before rate-limit filter; narrow `permitAll` to `/api/v1/auth/**`, `/api/v1/public/**`, `/api/v1/badges/**`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`; all else `authenticated`
- `com.habit.infra.security.RateLimitFilter` — read from `SecurityContextHolder`
- `com.habit.api.SharedBadgeController` — `@AuthenticationPrincipal Long userId`

## Parallel Plan

- **Agent 1**: `AuthService` + `AuthController` + `AuthDtos`. Uses `UserRepository`, `PasswordEncoder`, `JwtService`.
- **Main thread**: everything security-adjacent (properties, service, filter, config updates, rate-limit migration, SharedBadge migration, User change, ErrorCode change).

## What NOT to change

- Do not add refresh tokens in this step.
- Do not add role-based authorization; everything is "authenticated or not."
- Do not touch `/api/v1/public/**` or `/api/v1/badges/**` auth — they stay public.
