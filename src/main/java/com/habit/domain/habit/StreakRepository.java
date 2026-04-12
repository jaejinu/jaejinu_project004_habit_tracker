package com.habit.domain.habit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StreakRepository extends JpaRepository<Streak, Long> {

    Optional<Streak> findByHabitId(Long habitId);

    List<Streak> findAllByHabitIdIn(List<Long> habitIds);

    @Query("SELECT COALESCE(MAX(s.longestStreak), 0) FROM Streak s WHERE s.habitId IN :habitIds")
    int findMaxLongestByHabitIdIn(@Param("habitIds") List<Long> habitIds);

    @Query("SELECT s FROM Streak s WHERE s.currentStreak > 0 AND s.lastCheckedDate < :cutoff")
    List<Streak> findAllPotentiallyBroken(@Param("cutoff") LocalDate cutoff);
}
