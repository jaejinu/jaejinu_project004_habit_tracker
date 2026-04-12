package com.habit.api;

import com.habit.domain.habit.Habit;
import com.habit.domain.habit.HabitFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class HabitDtos {

    private HabitDtos() {}

    public record HabitCreateRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 500) String description,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color,
        @Size(max = 16) String icon,
        @NotNull HabitFrequency frequency,
        Integer targetDays,
        @NotNull LocalDate startDate,
        LocalDate endDate
    ) {}

    public record HabitUpdateRequest(
        @Size(max = 100) String title,
        @Size(max = 500) String description,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color,
        @Size(max = 16) String icon,
        Integer targetDays,
        LocalDate endDate
    ) {}

    public record VisibilityRequest(@NotNull Boolean isPublic) {}

    public record HabitResponse(
        Long id,
        String title,
        String description,
        String color,
        String icon,
        HabitFrequency frequency,
        Integer targetDays,
        boolean isPublic,
        LocalDate startDate,
        LocalDate endDate,
        int currentStreak,
        int longestStreak,
        int totalDays
    ) {
        public static HabitResponse of(Habit h, int currentStreak, int longestStreak, int totalDays) {
            return new HabitResponse(
                h.getId(),
                h.getTitle(),
                h.getDescription(),
                h.getColor(),
                h.getIcon(),
                h.getFrequency(),
                h.getTargetDays(),
                h.isPublic(),
                h.getStartDate(),
                h.getEndDate(),
                currentStreak,
                longestStreak,
                totalDays
            );
        }
    }
}
