package com.habit.domain.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitStatsRepository extends JpaRepository<HabitStats, Long> {

    List<HabitStats> findAllByHabitIdAndStatsDateBetween(Long habitId, LocalDate start, LocalDate end);

    Optional<HabitStats> findByHabitIdAndStatsDate(Long habitId, LocalDate date);
}
