package com.habit.domain.habit;

import com.habit.common.exception.BusinessException;
import com.habit.common.exception.ErrorCode;
import com.habit.domain.badge.SharedBadgeRepository;
import com.habit.domain.stats.HabitStatsRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HabitService {

    private final HabitRepository habitRepository;
    private final StreakRepository streakRepository;
    private final CheckInRepository checkInRepository;
    private final HabitStatsRepository habitStatsRepository;
    private final SharedBadgeRepository sharedBadgeRepository;

    public List<Habit> listMine(Long userId) {
        return habitRepository.findAllByUserId(userId);
    }

    public List<Habit> listPublicOf(Long userId) {
        return habitRepository.findAllByUserIdAndIsPublicTrue(userId);
    }

    public Habit get(Long userId, Long habitId) {
        return habitRepository.findByIdAndUserId(habitId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.HABIT_NOT_FOUND));
    }

    @Transactional
    public Habit create(Long userId, String title, HabitFrequency frequency, LocalDate startDate) {
        Habit habit = habitRepository.save(Habit.create(userId, title, frequency, startDate));
        streakRepository.save(Streak.initFor(habit.getId()));
        return habit;
    }

    @Transactional
    public Habit updateBasic(Long userId, Long habitId, String title, String description, String color, String icon) {
        Habit habit = get(userId, habitId);
        habit.updateBasic(title, description, color, icon);
        return habit;
    }

    @Transactional
    public Habit togglePublic(Long userId, Long habitId, boolean isPublic) {
        Habit habit = get(userId, habitId);
        habit.togglePublic(isPublic);
        return habit;
    }

    @Transactional
    public void delete(Long userId, Long habitId) {
        Habit habit = get(userId, habitId);
        sharedBadgeRepository.deleteAllByHabitId(habitId);
        habitStatsRepository.deleteAllByHabitId(habitId);
        checkInRepository.deleteAllByHabitId(habitId);
        streakRepository.findByHabitId(habitId).ifPresent(streakRepository::delete);
        habitRepository.delete(habit);
    }
}
