# Step 3 — Public Badge API, SVG Generation, Rate Limit (Ultra-Plan)

Single source of truth for step 3 parallel work.

## Endpoint Inventory

| Method | Path | Auth | Rate-limit |
|---|---|---|---|
| GET | `/api/v1/badges/{token}/calendar.svg` | public | exempt (long CDN cache) |
| GET | `/api/v1/badges/{token}/streak.svg` | public | exempt |
| GET | `/api/v1/badges/{token}/total.svg` | public | exempt |
| GET | `/api/v1/public/{publicId}/habits` | public | anon bucket |
| GET | `/api/v1/public/{publicId}/stats` | public | anon bucket |
| POST | `/api/v1/habits/{habitId}/badges` | auth (stub header) | user bucket |

**Temporary auth stub**: until JWT is wired, the habit-owner endpoint reads `X-User-Id` header (Long). Controller code must NOT assume auth is real — add a TODO comment pointing to a future `SecurityContextHolder`-based userId lookup. Do NOT build JWT in this step.

## Rate Limit

Redis sliding-window counter, 1h window, fixed-bucket (per-hour).

- Key: `rate:{scope}:{id}:{yyyyMMddHH}`
  - scope = `ip` for anonymous, `user` for authenticated
  - id = IP address or userId
- Limit: 60/h (anonymous), 1000/h (authenticated)
- Response on exceed: HTTP 429 with `ErrorResponse` body (code `COMMON_429`) and `Retry-After: <seconds-until-next-hour>` header.

Paths **exempt** from rate limit:
- `/api/v1/badges/**` (handled by CDN per project spec)
- `/actuator/**`
- `/swagger-ui/**`, `/v3/api-docs/**`

Implementation: one `OncePerRequestFilter` registered as a bean. Before `FilterRegistrationBean`, set order after Spring Security so authenticated userId is resolvable when auth eventually lands — for now it reads `X-User-Id` header.

## SVG Caching

`BadgeCacheService` uses `StringRedisTemplate` directly (not `@Cacheable`) because we need SVG as a raw string with stable key format.

- Key: `badge:svg:{token}:{type}:{paramsHash}`
  - `type` ∈ `calendar | streak | total`
  - `paramsHash` = hex SHA-1(6 chars prefix) of the normalized query string (sorted k=v joined by `&`).
- TTL: 1 hour.
- Stampede guard: on cache miss, attempt `SETNX` on `badge:svg:lock:{...}` with 10s TTL; if not acquired, wait 100ms and retry the read twice before falling through to generation (avoid dogpile on hot tokens).

HTTP response headers for every SVG response:
- `Content-Type: image/svg+xml; charset=utf-8`
- `Cache-Control: public, max-age=3600, s-maxage=3600, stale-while-revalidate=600`
- `ETag: "<sha1-first-16-of-svg-body>"`
- `Vary: Accept-Encoding`

If the client sends `If-None-Match` matching the ETag, return **304** with empty body.

## Query Parameters (all SVG endpoints)

| Param | Values | Applies to | Notes |
|---|---|---|---|
| `color` | 6-hex (without `#`) | calendar | overrides habit color |
| `theme` | `light` (default) \| `dark` | all | dark = muted background `#0d1117`, text `#c9d1d9` |
| `lang` | `en` (default) \| `ko` | streak, total | label text |
| `width` | integer ≥ 100 | streak, total | forces min width; actual width may grow for text |

Unknown params are ignored. Invalid `color` → fall back to habit color.

## SVG Generators

Package: `com.habit.domain.badge`

All generators are pure (no Spring beans, just static utilities with an `@Component` wrapper for DI if needed). Prefer `@Component` stateless classes with `public String generate(...)`.

### CalendarSvgGenerator

Input: `List<LocalDate> checkedDates`, `LocalDate anchor` (latest day to render, usually today), `CalendarOptions options`.

`CalendarOptions` record: `String fillColor`, `String emptyColor`, `String bgColor`, `String textColor`.

Theme mapping:
- light: fillColor = habit color, emptyColor = `#ebedf0`, bgColor = `#ffffff`, textColor = `#24292f`.
- dark: fillColor = habit color, emptyColor = `#161b22`, bgColor = `#0d1117`, textColor = `#c9d1d9`.

Output spec:
- Canvas: 53 columns × 7 rows of 11px cells with 3px gaps, plus padding.
- Total width = `53 * 14 + 30` = 772; height = `7 * 14 + 30` = 128.
- Right-most column is the week containing `anchor`. Work backwards 52 weeks.
- Day rows: index 0 = Sunday (top) … index 6 = Saturday (bottom). Locale-neutral fixed.
- Each `<rect>` has `rx="2"`, `width="11"`, `height="11"`, `x = 15 + col*14`, `y = 15 + row*14`, `fill = fill|empty color`.
- Inside each rect a `<title>` element: ISO date + " — " + checked/unchecked label (English: "checked"/"no check-in"). This yields native tooltips.
- Days outside `[anchor-364 .. anchor]` rendered with `fillColor="transparent"` (they exist on the grid but aren't visible).
- Wrap in `<svg xmlns="http://www.w3.org/2000/svg" role="img" aria-label="habit calendar">` with a `<rect>` background covering canvas using `bgColor`.

### StreakBadgeGenerator

Input: `int currentStreak`, `StreakBadgeOptions options`.

`StreakBadgeOptions` record: `String label` (default by lang — en: `"streak"`, ko: `"연속"`), `String theme` (light|dark), `int minWidth`.

Output: shields.io-style badge.
- Height = 20.
- Left (label) rect: darker grey `#555` light / `#30363d` dark; right (value) rect: color based on streak length:
  - 0–2: `#8b949e` (grey)
  - 3–6: `#eab308` (amber)
  - 7–29: `#f97316` (orange)
  - 30+: `#dc2626` (red)
- Text: `font-family="Verdana,Geneva,DejaVu Sans,sans-serif" font-size="11"` white; put a subtle `text-shadow`-like darker duplicate offset +0.5px for readability (two `<text>` elements, the first with `fill-opacity="0.3"` fill="black" y+1).
- Value text is `"N일"` (ko) or `"Nd"` (en) or just `"N"` — use `"N"` for en, `"N일"` for ko.
- Label rect width = `labelPadding*2 + estimateWidth(labelText)`; value rect width = `valuePadding*2 + estimateWidth(valueText)`. Padding 8 each side.
- `estimateWidth(s)` heuristic: `(int) Math.ceil(s.length() * 6.5) + (hasKoreanChars ? s.length() * 4 : 0)`.
- Total width = max(minWidth, labelRectWidth + valueRectWidth).

### TotalBadgeGenerator

Same shape as streak badge. Input: `int totalDays`. Label default `"total"` (en) / `"총 달성"` (ko). Value color fixed `#2f81f7` (blue).

## Service Layer

### BadgeCacheService (`infra/cache/BadgeCacheService`)

Methods:
- `Optional<String> get(String key)` — `redis.opsForValue().get(key)`
- `void put(String key, String svg)` — `redis.opsForValue().set(key, svg, TTL)`
- `String keyFor(String token, String type, Map<String,String> params)` — sorted normalized key + SHA-1 prefix
- `<T> T computeIfAbsent(String key, Supplier<String> generator)` — with stampede guard (SETNX lock 10s; on miss of lock wait+retry twice)

### PublicBadgeService (`domain/badge/PublicBadgeService`)

- `String renderCalendar(String token, Map<String,String> params)`
- `String renderStreak(String token, Map<String,String> params)`
- `String renderTotal(String token, Map<String,String> params)`

Each:
1. Look up `SharedBadge` by token → 404 if missing.
2. Look up `Habit` by habit_id; if `!isPublic` → `BADGE_HABIT_NOT_PUBLIC` (403).
3. Build cache key via `BadgeCacheService.keyFor(...)`.
4. `computeIfAbsent`: fetch check-ins (last 365 days) or streak or counts, invoke the matching generator.
5. `sharedBadgeRepository.incrementViewCount(token)` (fire-and-forget — call even on cache hit; it's cheap).

### SharedBadgeService (`domain/badge/SharedBadgeService`)

- `SharedBadge issue(Long userId, Long habitId, BadgeType type)` — verifies habit owner + `isPublic`, persists a new `SharedBadge`.

## Controllers (package `com.habit.api`)

### BadgeController

```
GET /api/v1/badges/{token}/calendar.svg
GET /api/v1/badges/{token}/streak.svg
GET /api/v1/badges/{token}/total.svg
```

- Pull query params into `Map<String,String>` (whitelist: `color`, `theme`, `lang`, `width`).
- Delegate to `PublicBadgeService`.
- Build `ResponseEntity<String>` with headers defined above (including ETag 304 short-circuit).

### PublicController

```
GET /api/v1/public/{publicId}/habits
GET /api/v1/public/{publicId}/stats
```

- `publicId` is UUID.
- `habits` returns only public habits for that user, as a simple DTO list (id/title/color/icon/frequency/streak — `id` here is habit id, which IS exposed because it's visible via habit ownership; the public URL key is `User.publicId`, not habit id).
- `stats` returns `{ totalCheckIns, totalHabits, longestAnyStreak }` aggregate (use simple count queries; longestAnyStreak can be `MAX(longest_streak)` from streaks joined to public habits).

### SharedBadgeController

```
POST /api/v1/habits/{habitId}/badges
  headers: X-User-Id (temporary)
  body: { "type": "CALENDAR" | "STREAK" | "TOTAL" }
  200: { "token": "...", "url": "/api/v1/badges/.../calendar.svg" }
```

## SecurityConfig (`infra/security/SecurityConfig`)

Step 3 stub: permit all, disable CSRF, stateless sessions. Add `// TODO: step-N will add JWT filter` comment pointing to future work. Configure so `/api/v1/badges/**`, `/api/v1/public/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/**` are all accessible without auth. For step 3, so is everything else (temporary).

## Errors

Add to `ErrorCode`:
- `BADGE_INVALID_PARAM` (400)

Already present (from step 2): `BADGE_TOKEN_NOT_FOUND`, `BADGE_HABIT_NOT_PUBLIC`, `RATE_LIMIT_EXCEEDED`.

## Parallelization Plan

- **Agent A**: `CalendarSvgGenerator` + `CalendarOptions` record (+ theme mapping helper).
- **Agent B**: `StreakBadgeGenerator` + `TotalBadgeGenerator` + shared `BadgeColor` helper.
- **Agent C**: `RateLimitFilter` + `FilterRegistrationBean` config + `SecurityConfig` stub.

Main thread (sequential after A/B/C land): `BadgeCacheService`, `PublicBadgeService`, `SharedBadgeService`, controllers, `ErrorCode` additions, README/memory updates.
