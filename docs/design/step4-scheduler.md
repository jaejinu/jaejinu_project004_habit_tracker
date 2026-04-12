# Step 4 — Scheduled Jobs, Drift Correction, Metrics (Ultra-Plan)

Single source of truth for step 4.

## Job Schedule (all UTC)

| Job | Cron | Purpose |
|---|---|---|
| `StreakMaintenanceJob` | `0 10 0 * * *` (every day 00:10) | Reset `currentStreak` to 0 for habits whose `lastCheckedDate < today - 1` (user missed yesterday). Fast, cheap. |
| `DailyStatsAggregationJob` | `0 5 2 * * *` (every day 02:05) | For each active habit, upsert a `HabitStats` row for **yesterday** recording `isChecked = presence of CheckIn(habit_id, yesterday)`. |
| `WeeklyStreakRecalcJob` | `0 0 3 * * SUN` (Sunday 03:00) | Full LAG-based recalculation via `StreakService.recalculate(habitId, today)` for every active habit — drift correction. |

Each `@Scheduled` method is annotated `@ConditionalOnProperty(name = "app.scheduler.enabled", matchIfMissing = true)` so tests disable them with `app.scheduler.enabled=false`.

**Active habit** for job iteration: `habit.start_date <= today AND (habit.end_date IS NULL OR habit.end_date >= today)`.

## Broken Streak Semantics

A streak is considered *broken* between check-ins when, at 00:10 UTC, we observe:

- `Streak.currentStreak > 0` AND
- `Streak.lastCheckedDate < today - 1 day`

Because `Streak.applyCheckIn` only runs on new check-ins, a user who simply stops checking in will leave their `currentStreak` stale. `StreakMaintenanceJob` resets it to 0 and bumps the `habit.streak.broken.total` counter.

We do NOT touch `longestStreak` — that is a historical max and stays.

## Daily Aggregation Idempotency

`HabitStats` has `uk_habit_stats_habit_date` on `(habit_id, stats_date)`. The aggregation job must not duplicate rows if it runs twice or is backfilled.

Strategy: for each (habit, yesterday), check `HabitStatsRepository.findByHabitIdAndStatsDate`; if absent, insert; if present, skip. The window is small (one day per habit per run) so N round-trips are acceptable for MVP. A bulk upsert via native SQL is a future optimization.

## Metrics (Micrometer)

Register via `MeterRegistry` (Spring Boot autoconfigures with Prometheus endpoint).

| Metric | Type | Tags | Recorded in |
|---|---|---|---|
| `habit.checkin.total` | Counter | (none) | `CheckInService.checkIn` on successful save |
| `habit.streak.broken.total` | Counter | (none) | `StreakMaintenanceJob` per broken habit |
| `habit.stats.aggregation.duration` | Timer | (none) | `DailyStatsAggregationJob` whole-run |
| `habit.stats.aggregation.rows` | Counter | (none) | `DailyStatsAggregationJob` per inserted row |
| `badge.svg.cache.hit.total` | Counter | `type` (calendar\|streak\|total) — pass via caller | `BadgeCacheService.computeIfAbsent` on hit |
| `badge.svg.cache.miss.total` | Counter | `type` | `BadgeCacheService.computeIfAbsent` on miss |
| `badge.svg.generation.duration` | Timer | `type` | wraps the `generator.get()` call |

`BadgeCacheService.computeIfAbsent` gains an overload that takes a `type` tag string, so the callers in `PublicBadgeService` can pass `"calendar"` / `"streak"` / `"total"`. Metrics themselves have no PII — tag values are a closed set.

## Repository Additions

- `HabitRepository.findAllActiveOn(LocalDate date)` — JPQL:
  ```
  SELECT h FROM Habit h WHERE h.startDate <= :date AND (h.endDate IS NULL OR h.endDate >= :date)
  ```
- `StreakRepository.findAllPotentiallyBroken(LocalDate cutoff)` — JPQL:
  ```
  SELECT s FROM Streak s WHERE s.currentStreak > 0 AND s.lastCheckedDate < :cutoff
  ```
  Caller passes `cutoff = today.minusDays(1)`.

## Services

- `HabitStatsService` (new, in `com.habit.domain.stats`):
  - `boolean recordFor(Long habitId, LocalDate date, boolean isChecked)` — returns `true` if inserted, `false` if row already existed.

## Application Properties

Add to `application.yml`:
```yaml
app:
  scheduler:
    enabled: true
```
And to test profile override (`application-test.yml`, create if missing): `app.scheduler.enabled: false`. For step 4 we just document this; don't add a test profile yml file yet unless step 5 needs it.

## Parallelization Plan

- **Main thread (now)**: write this design doc, add repo methods, write `HabitStatsService`, define metric names.
- **Agent A**: `DailyStatsAggregationJob` — iterates active habits, calls `HabitStatsService.recordFor(habitId, yesterday, hasCheckIn)`, wraps whole run in `Timer.record(...)`, logs summary.
- **Agent B**: `StreakMaintenanceJob` + `WeeklyStreakRecalcJob` — both in `infra/scheduler/`. Maintenance: reset broken streaks + emit counter. Weekly: iterate active habits and call `StreakService.recalculate(habitId, today)`.
- **Main thread (after)**: wire metrics into `CheckInService` and `BadgeCacheService`, update docs.

## Implementation Notes for Jobs

- All jobs are `@Component @RequiredArgsConstructor @Slf4j`.
- All jobs annotate the scheduled method with `@ConditionalOnProperty(name = "app.scheduler.enabled", matchIfMissing = true)` — wait, this doesn't work on methods. Instead put `@ConditionalOnProperty` on the class. Tests disabling the flag will then not instantiate the bean at all.
- Each `@Scheduled` method is `@Transactional` with `readOnly = false` because writes happen.
- Use `java.time.LocalDate.now(ZoneOffset.UTC)` and `java.time.Clock` injection path. For step 4 keep it simple: `LocalDate today = LocalDate.now(ZoneOffset.UTC);` at the top of each method. Do NOT use system default zone.
- Use paginated iteration if habit count >> 10k. For MVP, `findAll`-style is fine. Add a `// TODO: paginate when habit count > 10k` comment in the iteration site.
