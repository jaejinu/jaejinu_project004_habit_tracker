package com.habit.api;

import com.habit.common.exception.BusinessException;
import com.habit.common.exception.ErrorCode;
import com.habit.domain.habit.CheckInRepository;
import com.habit.domain.habit.Habit;
import com.habit.domain.habit.HabitFrequency;
import com.habit.domain.habit.HabitRepository;
import com.habit.domain.habit.Streak;
import com.habit.domain.habit.StreakRepository;
import com.habit.domain.user.User;
import com.habit.domain.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Tag(name = "Public", description = "사용자 공개 프로필 — 인증 없이 조회 가능")
public class PublicController {

    private final UserRepository userRepository;
    private final HabitRepository habitRepository;
    private final CheckInRepository checkInRepository;
    private final StreakRepository streakRepository;

    @GetMapping("/{publicId}/habits")
    @SecurityRequirements({})
    @Operation(summary = "사용자 공개 습관 목록")
    public List<PublicHabitDto> habits(
        @Parameter(description = "User.publicId (UUID)", example = "3f9e1c2a-...")
        @PathVariable UUID publicId) {
        User user = userRepository.findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Habit> habits = habitRepository.findAllByUserIdAndIsPublicTrue(user.getId());
        if (habits.isEmpty()) return List.of();

        List<Long> habitIds = habits.stream().map(Habit::getId).toList();
        Map<Long, Streak> streaksByHabit = streakRepository.findAllByHabitIdIn(habitIds).stream()
            .collect(Collectors.toMap(Streak::getHabitId, s -> s));

        return habits.stream()
            .map(h -> new PublicHabitDto(
                h.getId(),
                h.getTitle(),
                h.getColor(),
                h.getIcon(),
                h.getFrequency(),
                streaksByHabit.getOrDefault(h.getId(), Streak.initFor(h.getId())).getCurrentStreak(),
                streaksByHabit.getOrDefault(h.getId(), Streak.initFor(h.getId())).getLongestStreak()
            ))
            .toList();
    }

    @GetMapping("/{publicId}/stats")
    @SecurityRequirements({})
    @Operation(summary = "사용자 공개 통계 (체크인 합계/습관 수/최장 스트릭)")
    public PublicStatsDto stats(
        @Parameter(description = "User.publicId (UUID)", example = "3f9e1c2a-...")
        @PathVariable UUID publicId) {
        User user = userRepository.findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Habit> habits = habitRepository.findAllByUserIdAndIsPublicTrue(user.getId());
        if (habits.isEmpty()) return new PublicStatsDto(0L, 0, 0);

        List<Long> habitIds = habits.stream().map(Habit::getId).toList();
        long totalCheckIns = checkInRepository.countByHabitIdIn(habitIds);
        int longestAny = streakRepository.findMaxLongestByHabitIdIn(habitIds);
        return new PublicStatsDto(totalCheckIns, habits.size(), longestAny);
    }

    public record PublicHabitDto(
        Long id,
        String title,
        String color,
        String icon,
        HabitFrequency frequency,
        int currentStreak,
        int longestStreak
    ) {}

    public record PublicStatsDto(long totalCheckIns, int totalHabits, int longestAnyStreak) {}
}
