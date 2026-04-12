package com.habit.domain.habit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    @Modifying
    @Query("DELETE FROM CheckIn c WHERE c.habitId = :habitId")
    int deleteAllByHabitId(@Param("habitId") Long habitId);

    Optional<CheckIn> findByHabitIdAndCheckedDate(Long habitId, LocalDate date);

    List<CheckIn> findAllByHabitIdAndCheckedDateBetween(Long habitId, LocalDate start, LocalDate end);

    boolean existsByHabitIdAndCheckedDate(Long habitId, LocalDate date);

    @Query(value = """
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
        """, nativeQuery = true)
    int recalcCurrentStreak(@Param("habitId") Long habitId, @Param("today") LocalDate today);

    @Query(value = """
        WITH ordered AS (
          SELECT checked_date,
                 checked_date - (ROW_NUMBER() OVER (ORDER BY checked_date))::int AS grp
          FROM check_ins
          WHERE habit_id = :habitId
        )
        SELECT COALESCE(MAX(cnt), 0) FROM (
          SELECT COUNT(*) AS cnt FROM ordered GROUP BY grp
        ) g
        """, nativeQuery = true)
    int recalcLongestStreak(@Param("habitId") Long habitId);

    long countByHabitId(Long habitId);

    @Query("SELECT MAX(c.checkedDate) FROM CheckIn c WHERE c.habitId = :habitId")
    Optional<LocalDate> findLastCheckedDate(@Param("habitId") Long habitId);

    long countByHabitIdIn(List<Long> habitIds);
}
