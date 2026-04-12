package com.habit.domain.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HabitStatsRepository extends JpaRepository<HabitStats, Long> {

    List<HabitStats> findAllByHabitIdAndStatsDateBetween(Long habitId, LocalDate start, LocalDate end);

    Optional<HabitStats> findByHabitIdAndStatsDate(Long habitId, LocalDate date);

    @Modifying
    @Query("DELETE FROM HabitStats s WHERE s.habitId = :habitId")
    int deleteAllByHabitId(@Param("habitId") Long habitId);
}
