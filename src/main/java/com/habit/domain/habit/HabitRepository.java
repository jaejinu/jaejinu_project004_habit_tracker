package com.habit.domain.habit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HabitRepository extends JpaRepository<Habit, Long> {

    List<Habit> findAllByUserId(Long userId);

    List<Habit> findAllByUserIdAndIsPublicTrue(Long userId);

    Optional<Habit> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT h FROM Habit h WHERE h.startDate <= :date AND (h.endDate IS NULL OR h.endDate >= :date)")
    List<Habit> findAllActiveOn(@Param("date") LocalDate date);
}
