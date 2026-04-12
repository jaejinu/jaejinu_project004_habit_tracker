package com.habit.domain.habit;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StreakService {

    private final CheckInRepository checkInRepository;
    private final StreakRepository streakRepository;

    public Streak get(Long habitId) {
        return streakRepository.findByHabitId(habitId)
            .orElseGet(() -> Streak.initFor(habitId));
    }

    @Transactional
    public Streak recalculate(Long habitId, LocalDate today) {
        int current = checkInRepository.recalcCurrentStreak(habitId, today);
        int longest = checkInRepository.recalcLongestStreak(habitId);
        long total = checkInRepository.countByHabitId(habitId);
        LocalDate last = checkInRepository.findLastCheckedDate(habitId).orElse(null);

        Streak streak = streakRepository.findByHabitId(habitId)
            .orElseGet(() -> streakRepository.save(Streak.initFor(habitId)));
        streak.overwrite(current, longest, (int) total, last);
        return streak;
    }
}
