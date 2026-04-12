# Step 2 — Entity Design Specification (Ultra-Plan)

Single source of truth for parallel entity implementation. Any agent implementing step 2 entities must conform to this spec exactly.

## Global Rules

- **Package**: `com.habit.domain.<subdomain>` (e.g., `com.habit.domain.user`, `com.habit.domain.habit`).
- **Base class**: extend `com.habit.common.BaseTimeEntity` (provides `createdAt`, `updatedAt`).
- **Constructor**: `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + `@Builder` on a static factory pattern or on a private all-args constructor (use Lombok `@Builder` + `@AllArgsConstructor(access = AccessLevel.PRIVATE)`).
- **Getters**: `@Getter` from Lombok. No public setters; use domain methods to mutate.
- **Table name**: explicit, snake_case plural (e.g., `@Table(name = "users")`, `@Table(name = "habits")`).
- **Column names**: explicit snake_case via `@Column(name = "...")` on every field that isn't a trivial single-word match.
- **IDs**: `Long id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`, column `id`.
- **FKs**: stored as raw `Long userId` / `Long habitId`. **Do not** use `@ManyToOne`. Aggregate root pattern.
- **Public identifiers**: separate UUID/token fields. Never expose `id` in URLs.
- **Enums**: `@Enumerated(EnumType.STRING)` always.
- **Dates**: `LocalDate` for day-granular fields (stored as DATE), `Instant` for timestamps (stored as TIMESTAMP).
- **Nullability**: `@Column(nullable = false)` wherever domain requires it.
- **Indexes**: declare via `@Table(indexes = {@Index(...)})` and unique constraints via `@Table(uniqueConstraints = {@UniqueConstraint(...)})`.
- **Repositories**: `public interface XxxRepository extends JpaRepository<Xxx, Long>` in the same package.

## Entity: User (`com.habit.domain.user.User`)

Table: `users`

| Field | Type | Column | Constraints |
|---|---|---|---|
| id | Long | id | PK, identity |
| email | String | email | not null, unique (uniqueConstraint `uk_users_email`), len ≤ 254 |
| nickname | String | nickname | not null, len ≤ 50 |
| publicId | UUID | public_id | not null, unique (`uk_users_public_id`) |
| githubUsername | String | github_username | nullable, len ≤ 100 |
| createdAt / updatedAt | (from base) | | |

Domain methods:
- `static User create(String email, String nickname)` — generates `publicId = UUID.randomUUID()`.
- `void linkGithub(String githubUsername)`.
- `void updateNickname(String nickname)`.

Repository: `UserRepository`
- `Optional<User> findByEmail(String email)`
- `Optional<User> findByPublicId(UUID publicId)`
- `boolean existsByEmail(String email)`

## Entity: Habit (`com.habit.domain.habit.Habit`)

Table: `habits`

Enum `HabitFrequency` in same package: `DAILY`, `WEEKLY`, `CUSTOM`.

| Field | Type | Column | Constraints |
|---|---|---|---|
| id | Long | id | PK, identity |
| userId | Long | user_id | not null, indexed (`ix_habits_user_id`) |
| title | String | title | not null, len ≤ 100 |
| description | String | description | nullable, len ≤ 500 |
| color | String | color | not null, default `#216e39` at application level (hex 7-char) |
| icon | String | icon | nullable, len ≤ 16 (emoji) |
| frequency | HabitFrequency | frequency | not null, STRING enum |
| targetDays | Integer | target_days | nullable (bitmask 0–127 for Mon=1 ... Sun=64, only meaningful when `frequency = WEEKLY`) |
| isPublic | boolean | is_public | not null, default `false` |
| startDate | LocalDate | start_date | not null |
| endDate | LocalDate | end_date | nullable |

Domain methods:
- `static Habit create(Long userId, String title, HabitFrequency frequency, LocalDate startDate)` — color defaults `#216e39`, isPublic defaults false.
- `void updateBasic(String title, String description, String color, String icon)`.
- `void togglePublic(boolean isPublic)`.
- `boolean isActiveOn(LocalDate date)` — true if `startDate <= date` and (`endDate == null || date <= endDate`).

Repository: `HabitRepository`
- `List<Habit> findAllByUserId(Long userId)`
- `List<Habit> findAllByUserIdAndIsPublicTrue(Long userId)`
- `Optional<Habit> findByIdAndUserId(Long id, Long userId)`

## Entity: CheckIn (`com.habit.domain.habit.CheckIn`)

Table: `check_ins`

Enum `Mood` in same package: `GREAT`, `GOOD`, `OKAY`, `BAD`.

| Field | Type | Column | Constraints |
|---|---|---|---|
| id | Long | id | PK, identity |
| habitId | Long | habit_id | not null |
| userId | Long | user_id | not null |
| checkedDate | LocalDate | checked_date | not null |
| note | String | note | nullable, len ≤ 500 |
| mood | Mood | mood | nullable, STRING enum |

Table-level:
- Unique constraint `uk_check_ins_habit_date` on `(habit_id, checked_date)`.
- Index `ix_check_ins_user_date` on `(user_id, checked_date)`.
- Index `ix_check_ins_habit_date` on `(habit_id, checked_date)` (overlaps unique but helps for range queries; OK).

Domain methods:
- `static CheckIn create(Long habitId, Long userId, LocalDate checkedDate, String note, Mood mood)`.
- `void updateNote(String note, Mood mood)`.

Repository: `CheckInRepository`
- `Optional<CheckIn> findByHabitIdAndCheckedDate(Long habitId, LocalDate date)`
- `List<CheckIn> findAllByHabitIdAndCheckedDateBetween(Long habitId, LocalDate start, LocalDate end)`
- `boolean existsByHabitIdAndCheckedDate(Long habitId, LocalDate date)`
- **Native query** method `int recalcCurrentStreak(Long habitId, LocalDate today)`:
  - Uses `LAG()` over ordered `checked_date` to count contiguous days ending at `today` or the latest date `<= today`. Include the query text exactly:
    ```sql
    WITH ordered AS (
      SELECT checked_date,
             checked_date - (ROW_NUMBER() OVER (ORDER BY checked_date))::int AS grp
      FROM check_ins
      WHERE habit_id = :habitId
        AND checked_date <= :today
    ),
    groups AS (
      SELECT grp, COUNT(*) AS len, MAX(checked_date) AS last_date
      FROM ordered
      GROUP BY grp
    )
    SELECT COALESCE(
      (SELECT len FROM groups
        WHERE last_date = :today
           OR last_date = :today - INTERVAL '1 day'
        ORDER BY last_date DESC LIMIT 1),
      0
    )
    ```
  - Declared with `@Query(value = "...", nativeQuery = true)`. Parameters `@Param("habitId") Long habitId, @Param("today") LocalDate today`.

## Entity: Streak (`com.habit.domain.habit.Streak`)

Table: `streaks`

One-to-one with Habit (habitId unique).

| Field | Type | Column | Constraints |
|---|---|---|---|
| id | Long | id | PK, identity |
| habitId | Long | habit_id | not null, unique (`uk_streaks_habit_id`) |
| currentStreak | int | current_streak | not null, default 0 |
| longestStreak | int | longest_streak | not null, default 0 |
| lastCheckedDate | LocalDate | last_checked_date | nullable |
| totalDays | int | total_days | not null, default 0 |

Domain methods:
- `static Streak initFor(Long habitId)` — zeros.
- `void applyCheckIn(LocalDate checkedDate)`:
  - If `lastCheckedDate == null` or `checkedDate.isAfter(lastCheckedDate.plusDays(1))`: `currentStreak = 1`.
  - Else if `checkedDate.equals(lastCheckedDate.plusDays(1))`: `currentStreak += 1`.
  - Else (`checkedDate.equals(lastCheckedDate)` or before): no-op (re-entrancy guard).
  - `longestStreak = max(longestStreak, currentStreak)`.
  - `totalDays += 1` only if `lastCheckedDate == null` or `!checkedDate.equals(lastCheckedDate)`.
  - `lastCheckedDate = checkedDate` when advanced.
- `void overwrite(int current, int longest, int total, LocalDate lastCheckedDate)` — used by recalc job.

Repository: `StreakRepository`
- `Optional<Streak> findByHabitId(Long habitId)`

## Entity: HabitStats (`com.habit.domain.stats.HabitStats`)

Table: `habit_stats`

| Field | Type | Column | Constraints |
|---|---|---|---|
| id | Long | id | PK, identity |
| habitId | Long | habit_id | not null |
| statsDate | LocalDate | stats_date | not null |
| weekNumber | int | week_number | not null (ISO week) |
| monthNumber | int | month_number | not null (1–12) |
| isChecked | boolean | is_checked | not null |

- Unique constraint `uk_habit_stats_habit_date` on `(habit_id, stats_date)`.
- Index `ix_habit_stats_habit_month` on `(habit_id, month_number)`.

Domain methods:
- `static HabitStats of(Long habitId, LocalDate date, boolean isChecked)` — computes ISO week and month.

Repository: `HabitStatsRepository`
- `List<HabitStats> findAllByHabitIdAndStatsDateBetween(Long habitId, LocalDate start, LocalDate end)`
- `Optional<HabitStats> findByHabitIdAndStatsDate(Long habitId, LocalDate date)`

## Entity: SharedBadge (`com.habit.domain.badge.SharedBadge`)

Table: `shared_badges`

Enum `BadgeType` in same package: `STREAK`, `TOTAL`, `CALENDAR`.

| Field | Type | Column | Constraints |
|---|---|---|---|
| id | Long | id | PK, identity |
| habitId | Long | habit_id | not null |
| userId | Long | user_id | not null |
| badgeType | BadgeType | badge_type | not null, STRING enum |
| publicToken | String | public_token | not null, unique (`uk_shared_badges_token`), len 32 (URL-safe base64 of 24 random bytes) |
| viewCount | long | view_count | not null, default 0 |

- Index `ix_shared_badges_habit` on `(habit_id)`.

Domain methods:
- `static SharedBadge create(Long habitId, Long userId, BadgeType type)` — generates `publicToken` via `SecureRandom` (24 bytes, URL-safe base64, no padding, trimmed to 32).
- `void recordView()` — atomic write via `UPDATE ... SET view_count = view_count + 1` is preferred in repository (see below), but this method also exists for in-memory mutation.

Repository: `SharedBadgeRepository`
- `Optional<SharedBadge> findByPublicToken(String token)`
- `List<SharedBadge> findAllByHabitId(Long habitId)`
- `@Modifying @Query("UPDATE SharedBadge s SET s.viewCount = s.viewCount + 1 WHERE s.publicToken = :token") int incrementViewCount(@Param("token") String token);`

## Checklist for implementers

- [ ] Extends `BaseTimeEntity`
- [ ] Matches table name, column names exactly
- [ ] Declares indexes and unique constraints via `@Table`
- [ ] No public setters; domain methods mutate state
- [ ] `@Builder` + private all-args constructor + protected no-arg
- [ ] `@Enumerated(EnumType.STRING)` on all enum fields
- [ ] Repository interface in same package, method signatures match exactly
- [ ] Javadoc not required; keep files terse
