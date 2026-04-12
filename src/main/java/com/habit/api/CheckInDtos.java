package com.habit.api;

import com.habit.domain.habit.CheckIn;
import com.habit.domain.habit.Mood;
import com.habit.domain.habit.Streak;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class CheckInDtos {

    private CheckInDtos() {}

    public record CheckInRequest(
        LocalDate checkedDate,
        @Size(max = 500) String note,
        Mood mood
    ) {}

    public record CheckInUpdateRequest(
        @Size(max = 500) String note,
        Mood mood
    ) {}

    public record StreakSnapshot(
        int currentStreak,
        int longestStreak,
        int totalDays,
        LocalDate lastCheckedDate
    ) {
        public static StreakSnapshot of(Streak s) {
            return new StreakSnapshot(
                s.getCurrentStreak(),
                s.getLongestStreak(),
                s.getTotalDays(),
                s.getLastCheckedDate()
            );
        }
    }

    public record CheckInResponse(
        Long id,
        Long habitId,
        LocalDate checkedDate,
        String note,
        Mood mood
    ) {
        public static CheckInResponse of(CheckIn c) {
            return new CheckInResponse(
                c.getId(),
                c.getHabitId(),
                c.getCheckedDate(),
                c.getNote(),
                c.getMood()
            );
        }
    }

    public record CheckInCreateResponse(CheckInResponse checkIn, StreakSnapshot streak) {}
}
