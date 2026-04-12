# Step 6 — Habit CRUD + Check-In Endpoints (Ultra-Plan)

Closes the "service exists, controller TODO" markers from `docs/api-reference.md`.

## Endpoint Inventory

All routes require `Authorization: Bearer <token>`. Principal = `Long userId` (from JWT).

### Habits

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/habits` | List the authenticated user's habits |
| GET | `/api/v1/habits/{habitId}` | Get a single habit (with current streak) |
| POST | `/api/v1/habits` | Create a habit |
| PATCH | `/api/v1/habits/{habitId}` | Update title/description/color/icon |
| PATCH | `/api/v1/habits/{habitId}/visibility` | Toggle `isPublic` |
| DELETE | `/api/v1/habits/{habitId}` | Delete a habit (cascades: check-ins, streak, stats, badges) |

### Check-Ins

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/habits/{habitId}/check-ins` | Record today's (or specified date's) check-in |
| GET | `/api/v1/habits/{habitId}/check-ins?from=&to=` | List check-ins in date window |
| PATCH | `/api/v1/habits/{habitId}/check-ins/{date}` | Update note/mood on an existing check-in |

## Request/Response Shapes

### `POST /api/v1/habits`
Request:
```json
{
  "title": "매일 코딩",
  "description": "1시간 이상",
  "color": "#216e39",
  "icon": "💻",
  "frequency": "DAILY",
  "startDate": "2026-04-12",
  "endDate": null
}
```
Response: `HabitDto` (all habit fields + currentStreak, longestStreak).

### `PATCH /api/v1/habits/{habitId}`
Request (all fields optional — null means "keep current"):
```json
{ "title": "새 제목", "description": null, "color": "#2da44e", "icon": "🔥" }
```

### `PATCH /api/v1/habits/{habitId}/visibility`
Request:
```json
{ "isPublic": true }
```

### `POST /api/v1/habits/{habitId}/check-ins`
Request (all optional):
```json
{ "checkedDate": "2026-04-12", "note": "오늘 잘 했음", "mood": "GOOD" }
```
- `checkedDate` defaults to `LocalDate.now(ZoneOffset.UTC)` if omitted.
- `mood` is one of `GREAT | GOOD | OKAY | BAD` or null.

Response: `{checkIn: {...}, streak: {currentStreak, longestStreak, totalDays, lastCheckedDate}}`.

### `GET /api/v1/habits/{habitId}/check-ins?from=&to=`
Both query params are ISO dates, optional. Defaults: `from = today - 29`, `to = today`.

### `PATCH /api/v1/habits/{habitId}/check-ins/{date}`
Request:
```json
{ "note": "수정된 메모", "mood": "GREAT" }
```

## Service-Layer Additions

- `HabitService.delete(Long userId, Long habitId)` — verifies ownership, deletes. For cascade: use explicit repository calls (no JPA `@OnDelete`) — delete order: badges → stats → check-ins → streak → habit.
- `CheckInService.list(Long userId, Long habitId, LocalDate from, LocalDate to)` — verifies ownership, returns list.
- `CheckInService.updateNoteAndMood(Long userId, Long habitId, LocalDate date, String note, Mood mood)` — finds the check-in, verifies ownership via its `userId`, updates.

## Authorization Invariants

- Every endpoint verifies `habitRepository.findByIdAndUserId(habitId, userId)` before any operation (except lookups scoped by userId already).
- For nested resources (`check-ins/{date}`), the habit check also enforces check-in ownership.

## DTOs (package `com.habit.api.dto`)

Keep DTOs close to the controllers. Prefer records.

- `HabitDtos.HabitCreateRequest`
- `HabitDtos.HabitUpdateRequest`
- `HabitDtos.VisibilityRequest`
- `HabitDtos.HabitResponse`
- `CheckInDtos.CheckInRequest`
- `CheckInDtos.CheckInResponse`
- `CheckInDtos.CheckInUpdateRequest`
- `CheckInDtos.StreakSnapshot`

Bean-validation: `@NotBlank`, `@Size`, `@NotNull`, `@Pattern` (for color hex).

## Errors (already defined)

- `HABIT_NOT_FOUND` (404), `HABIT_FORBIDDEN` (403)
- `CHECKIN_ALREADY_EXISTS` (409), `CHECKIN_OUT_OF_RANGE` (400)
- `INVALID_REQUEST` (400) — for validation failures via `MethodArgumentNotValidException`

No new `ErrorCode` entries needed.

## OpenAPI Annotations

Each controller class gets `@Tag("Habits")` / `@Tag("Check-Ins")`. Each method gets `@Operation` + key `@ApiResponse`s. `@AuthenticationPrincipal Long userId` is hidden from docs automatically.

## Parallel Plan

- **Agent A**: `HabitController` + `HabitDtos` + add `delete` method to `HabitService` (extend, don't rewrite).
- **Agent B**: `CheckInController` + `CheckInDtos` + extend `CheckInService` with `list` and `updateNoteAndMood`.

Both agents read this doc for the authoritative shape. Both add OpenAPI annotations inline.

## What NOT to change

- Don't touch the existing public/badge endpoints.
- Don't change the streak-calculation logic.
- Don't introduce soft-delete. Hard delete with explicit cascade is fine for MVP.
- Don't add paging to list endpoints yet — window queries are small by design (habits per user ≈ <50; 30-day check-in window).
