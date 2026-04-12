package com.habit.domain.stats;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HabitStatsService {

    private final HabitStatsRepository habitStatsRepository;

    @Transactional
    public boolean recordFor(Long habitId, LocalDate date, boolean isChecked) {
        if (habitStatsRepository.findByHabitIdAndStatsDate(habitId, date).isPresent()) {
            return false;
        }
        habitStatsRepository.save(HabitStats.of(habitId, date, isChecked));
        return true;
    }
}
