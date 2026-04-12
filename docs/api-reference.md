# Habit Tracker API Reference

`v1` REST API for the habit-tracker service (Spring Boot 3.3.x).
Base URL (dev default): `http://localhost:8080`

> This document is the narrative counterpart to the live Swagger UI
> available at `/swagger-ui.html`. It is organized by resource and
> clearly marks endpoints that exist only as a service layer today
> (controller layer is still TODO for some CRUD surfaces).

---

## Authentication

The API uses **JWT (HS256)** bearer tokens.

- Obtain a token via `POST /api/v1/auth/login`.
- Attach it to every authenticated request:

  ```
  Authorization: Bearer <accessToken>
  ```

- Access tokens expire after `app.security.jwt.access-token-ttl`
  (default `PT1H` = 1 hour).

Paths that are **public** (no `Authorization` header needed):

- `/api/v1/auth/**`
- `/api/v1/public/**`
- `/api/v1/badges/**`
- `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/**`

---

## Common error shape

All non-2xx responses produced by the API use this JSON body
(`com.habit.common.exception.ErrorResponse`):

```json
{
  "code": "HABIT_404",
  "message": "습관을 찾을 수 없습니다.",
  "timestamp": "2026-04-12T03:14:15.926Z",
  "path": "/api/v1/habits/42",
  "errors": [
    { "field": "title", "reason": "must not be blank" }
  ]
}
```

`errors` is populated only on `400` validation failures; otherwise `null`.

### Error-code prefixes

| Prefix | Meaning | Examples |
|---|---|---|
| `COMMON_*` | Cross-cutting (validation, auth required, rate limit) | `COMMON_400`, `COMMON_401`, `COMMON_404`, `COMMON_429`, `COMMON_500` |
| `AUTH_*` | JWT / login failures | `AUTH_401_CRED`, `AUTH_401_TOKEN`, `AUTH_401_EXPIRED` |
| `USER_*` | User lookup / uniqueness | `USER_404`, `USER_409_EMAIL` |
| `HABIT_*` | Habit not found / not yours | `HABIT_404`, `HABIT_403` |
| `CHECKIN_*` | Check-in rules | `CHECKIN_409`, `CHECKIN_400_RANGE` |
| `BADGE_*` | Badge token / public-flag / params | `BADGE_404`, `BADGE_403`, `BADGE_400` |

---

## Rate limit

A Redis sliding-window counter guards the API.

| Scope | Limit | Key |
|---|---|---|
| Anonymous (by IP) | **60 / hour** | `rate:ip:{ip}:{yyyyMMddHH}` |
| Authenticated (by userId) | **1000 / hour** | `rate:user:{userId}:{yyyyMMddHH}` |

**Exempt paths** (never rate-limited):

- `/api/v1/badges/**` (SVG responses are cached at the edge for 1h)
- `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`

On exceed:

- Status `429 Too Many Requests`
- Body: `ErrorResponse` with `code = "COMMON_429"`
- Header: `Retry-After: <seconds-until-next-hour>`

---

# Auth

### `POST /api/v1/auth/signup`

- **Auth**: Public
- **Body**:

  ```json
  {
    "email": "jaejinu@example.com",
    "nickname": "jaejinu",
    "password": "pa$$w0rd123"
  }
  ```

  Rules: email format, `nickname` ≤ 50 chars, `password` 8–100 chars.

- **Response `200`**:

  ```json
  {
    "userId": 1,
    "publicId": "4b9e3c0f-5e7a-4e0f-b3b7-0a8e6c5b9d22",
    "email": "jaejinu@example.com",
    "nickname": "jaejinu"
  }
  ```

- **Errors**:

  | Status | Code | When |
  |---|---|---|
  | 400 | `COMMON_400` | 유효성 실패 (email / nickname / password) |
  | 409 | `USER_409_EMAIL` | 이미 가입된 이메일 |

### `POST /api/v1/auth/login`

- **Auth**: Public
- **Body**:

  ```json
  { "email": "jaejinu@example.com", "password": "pa$$w0rd123" }
  ```

- **Response `200`**:

  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
  ```

- **Errors**:

  | Status | Code | When |
  |---|---|---|
  | 401 | `AUTH_401_CRED` | 이메일/비밀번호 불일치 |
  | 400 | `COMMON_400` | 필드 검증 실패 |

---

# Habits

Implemented by `HabitController` at `com.habit.api.HabitController`. All endpoints require a Bearer token.

### `GET /api/v1/habits`

- **Auth**: Bearer
- **Response**: `HabitResponse[]` — the caller's habits joined with current streak data.

  ```json
  [
    {
      "id": 10,
      "title": "매일 코딩",
      "description": "1시간 이상",
      "color": "#2da44e",
      "icon": "💻",
      "frequency": "DAILY",
      "targetDays": null,
      "isPublic": true,
      "startDate": "2026-01-01",
      "endDate": null,
      "currentStreak": 7,
      "longestStreak": 21,
      "totalDays": 54
    }
  ]
  ```

### `GET /api/v1/habits/{habitId}`

- **Auth**: Bearer
- **Response**: single `HabitResponse` (same shape as list item).
- **Errors**: `404 HABIT_404` when the habit doesn't exist or isn't owned by the caller.

### `POST /api/v1/habits`

- **Auth**: Bearer
- **Body**:

  ```json
  {
    "title": "매일 코딩",
    "description": "1시간 이상",
    "color": "#216e39",
    "icon": "💻",
    "frequency": "DAILY",
    "targetDays": null,
    "startDate": "2026-04-12",
    "endDate": null
  }
  ```

  - `title` required (≤ 100 chars), `frequency` required, `startDate` required.
  - `color` must match `^#[0-9a-fA-F]{6}$` when provided; defaults to `#216e39`.
  - `targetDays` and `endDate` are accepted by the DTO but not yet persisted by the service — tracked as a TODO inside `HabitController`.

- **Response `201 Created`** + `Location: /api/v1/habits/{id}` header; body is the created `HabitResponse`.

### `PATCH /api/v1/habits/{habitId}`

- **Auth**: Bearer
- **Body** (any field null means "keep current"):

  ```json
  {
    "title": "매일 코딩 2시간",
    "description": "업데이트",
    "color": "#ff9800",
    "icon": "⌨️"
  }
  ```

- **Errors**: `404 HABIT_404`, `400 COMMON_400` (validation), e.g. invalid color hex.

### `PATCH /api/v1/habits/{habitId}/visibility`

- **Auth**: Bearer
- **Body**: `{ "isPublic": true }`
- **Response**: updated `HabitResponse`.

### `DELETE /api/v1/habits/{habitId}`

- **Auth**: Bearer
- **Response `204`**. Cascade: SharedBadges → HabitStats → CheckIns → Streak → Habit (single transaction).
- **Errors**: `404 HABIT_404`.

---

# Check-ins

Implemented by `CheckInController` at `com.habit.api.CheckInController`. All endpoints require a Bearer token.

### `POST /api/v1/habits/{habitId}/check-ins`

- **Auth**: Bearer
- **Body** (all fields optional — if `checkedDate` is omitted, defaults to today UTC):

  ```json
  {
    "checkedDate": "2026-04-12",
    "note": "오늘도 해냈다",
    "mood": "GOOD"
  }
  ```

  `mood` ∈ `GREAT | GOOD | OKAY | BAD`.

- **Response `201 Created`** + `Location: /api/v1/habits/{habitId}/check-ins/{date}` header; body is `CheckInCreateResponse`:

  ```json
  {
    "checkIn": {
      "id": 1234,
      "habitId": 10,
      "checkedDate": "2026-04-12",
      "note": "오늘도 해냈다",
      "mood": "GOOD"
    },
    "streak": {
      "currentStreak": 7,
      "longestStreak": 21,
      "totalDays": 54,
      "lastCheckedDate": "2026-04-12"
    }
  }
  ```

- **Errors**:

  | Status | Code | When |
  |---|---|---|
  | 409 | `CHECKIN_409` | 해당 날짜에 이미 체크인함 (Redis SETNX 선점 + DB 유니크 제약 이중 방어) |
  | 400 | `CHECKIN_400_RANGE` | 습관의 `startDate`~`endDate` 범위 밖 |
  | 404 | `HABIT_404` | 습관이 존재하지 않거나 소유자가 아님 |

### `GET /api/v1/habits/{habitId}/check-ins?from=&to=`

- **Auth**: Bearer
- **Query params**: `from`, `to` (ISO dates, optional). Default window: `[today - 29, today]` UTC.
- **Response**: `CheckInResponse[]`.

### `PATCH /api/v1/habits/{habitId}/check-ins/{date}`

- **Auth**: Bearer
- **Body**: `{ "note": "수정된 메모", "mood": "GREAT" }` — both optional.
- **Response**: updated `CheckInResponse`.
- **Errors**: `404 HABIT_404` or `404 COMMON_404` (check-in not found), `403 COMMON_403` (check-in belongs to another user — shouldn't happen when ownership check passes, but enforced defensively).

---

# Badges (shared-token management)

### `POST /api/v1/habits/{habitId}/badges`

- **Auth**: Bearer
- **Implemented**: `SharedBadgeController.issue`
- **Body**:

  ```json
  { "type": "CALENDAR" }
  ```

  `type` ∈ `CALENDAR | STREAK | TOTAL`.

- **Response `200`**:

  ```json
  {
    "token": "aB3kL9mN2pQ5rS7tU0vW-xYzA1bC2dE3fG4hI5jK6l",
    "url": "/api/v1/badges/aB3kL9mN2pQ5rS7tU0vW-xYzA1bC2dE3fG4hI5jK6l/calendar.svg"
  }
  ```

  The `url` is relative; prepend the server origin (dev: `http://localhost:8080`).

- **Errors**:

  | Status | Code | When |
  |---|---|---|
  | 401 | `COMMON_401` / `AUTH_401_TOKEN` | 토큰 누락/무효 |
  | 403 | `BADGE_403` | 습관이 비공개 (`isPublic=false`) |
  | 404 | `HABIT_404` | 습관이 존재하지 않거나 소유자가 아님 |

---

# Badges (public SVG)

All three endpoints below are **public** (no `Authorization` header),
**exempt from rate-limit**, and share the same caching/header behavior.

### Common response headers

| Header | Value |
|---|---|
| `Content-Type` | `image/svg+xml;charset=utf-8` |
| `Cache-Control` | `public, max-age=3600, s-maxage=3600, stale-while-revalidate=600` |
| `ETag` | `"<sha1-first-16-of-svg-body>"` |
| `Vary` | `Accept-Encoding` |

If the client sends `If-None-Match: "<etag>"` that matches the current
body hash, the server replies with **`304 Not Modified`** and empty body.

Redis caches the generated SVG for 1 hour per `(token, type, paramsHash)`.

### Common query parameters

| Param | Values | Applies to | Default / notes |
|---|---|---|---|
| `color` | 6-hex, no `#` (e.g. `2da44e`) | `calendar.svg` | Falls back to habit color if invalid |
| `theme` | `light` \| `dark` | all | `light` default |
| `lang` | `en` \| `ko` | `streak.svg`, `total.svg` | `en` default |
| `width` | integer ≥ 100 | `streak.svg`, `total.svg` | Minimum width; actual may grow for text |

Unknown query params are ignored silently.

### `GET /api/v1/badges/{token}/calendar.svg`

- **Implemented**: `BadgeController.calendar`
- Renders a 53×7 grass calendar (last 365 days).
- **Response `200`** (excerpt):

  ```xml
  <svg xmlns="http://www.w3.org/2000/svg" role="img" aria-label="habit calendar"
       width="772" height="128">
    <rect width="772" height="128" fill="#ffffff"/>
    <rect x="15" y="15" width="11" height="11" rx="2" fill="#2da44e">
      <title>2026-04-12 — checked</title>
    </rect>
    ...
  </svg>
  ```

### `GET /api/v1/badges/{token}/streak.svg`

- **Implemented**: `BadgeController.streak`
- Shields.io-style pill with current streak. Value color scales:
  `0–2` grey, `3–6` amber, `7–29` orange, `30+` red.

### `GET /api/v1/badges/{token}/total.svg`

- **Implemented**: `BadgeController.total`
- Same shape; value is total check-ins, fixed blue `#2f81f7`.

### Errors for all three

| Status | Code | When |
|---|---|---|
| 304 | — | `If-None-Match` matched |
| 400 | `BADGE_400` | 잘못된 파라미터 (드문 경우) |
| 403 | `BADGE_403` | 토큰은 유효하지만 해당 습관이 비공개로 전환됨 |
| 404 | `BADGE_404` | 토큰이 존재하지 않음 |

---

# Public profile

### `GET /api/v1/public/{publicId}/habits`

- **Auth**: Public
- **Implemented**: `PublicController.habits`
- `publicId` is a UUID (`User.publicId`), **not** the internal user id.
- Returns only habits with `isPublic = true`.
- **Response `200`**:

  ```json
  [
    {
      "id": 10,
      "title": "매일 코딩 1시간",
      "color": "2da44e",
      "icon": "💻",
      "frequency": "DAILY",
      "currentStreak": 7,
      "longestStreak": 21
    }
  ]
  ```

- **Errors**:

  | Status | Code | When |
  |---|---|---|
  | 404 | `USER_404` | `publicId`에 해당하는 사용자 없음 |

### `GET /api/v1/public/{publicId}/stats`

- **Auth**: Public
- **Implemented**: `PublicController.stats`
- Aggregate stats across that user's public habits only.
- **Response `200`**:

  ```json
  {
    "totalCheckIns": 342,
    "totalHabits": 3,
    "longestAnyStreak": 21
  }
  ```

- **Errors**: same as above.

---

## Change log

- 2026-04-12: Initial Markdown reference. Habit CRUD and check-in
  controllers are intentionally marked TODO — see `HabitService` /
  `CheckInService` for the eventual shape.
