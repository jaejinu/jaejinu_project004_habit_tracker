package com.habit.domain.badge;

import com.habit.common.exception.BusinessException;
import com.habit.common.exception.ErrorCode;
import com.habit.domain.habit.Habit;
import com.habit.domain.habit.HabitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SharedBadgeService {

    private final SharedBadgeRepository sharedBadgeRepository;
    private final HabitRepository habitRepository;

    @Transactional
    public SharedBadge issue(Long userId, Long habitId, BadgeType type) {
        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.HABIT_NOT_FOUND));
        if (!habit.isPublic()) {
            throw new BusinessException(ErrorCode.BADGE_HABIT_NOT_PUBLIC);
        }
        return sharedBadgeRepository.save(SharedBadge.create(habitId, userId, type));
    }
}
