package com.habit.domain.habit;

import com.habit.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "streaks",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_streaks_habit_id", columnNames = "habit_id")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Streak extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "habit_id", nullable = false)
    private Long habitId;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Column(name = "longest_streak", nullable = false)
    private int longestStreak;

    @Column(name = "last_checked_date")
    private LocalDate lastCheckedDate;

    @Column(name = "total_days", nullable = false)
    private int totalDays;

    public static Streak initFor(Long habitId) {
        return Streak.builder()
            .habitId(habitId)
            .currentStreak(0)
            .longestStreak(0)
            .lastCheckedDate(null)
            .totalDays(0)
            .build();
    }

    public void applyCheckIn(LocalDate checkedDate) {
        boolean advanced;
        if (lastCheckedDate == null || checkedDate.isAfter(lastCheckedDate.plusDays(1))) {
            currentStreak = 1;
            advanced = true;
        } else if (checkedDate.equals(lastCheckedDate.plusDays(1))) {
            currentStreak += 1;
            advanced = true;
        } else {
            return;
        }
        longestStreak = Math.max(longestStreak, currentStreak);
        if (advanced) {
            totalDays += 1;
            lastCheckedDate = checkedDate;
        }
    }

    public void overwrite(int current, int longest, int total, LocalDate lastCheckedDate) {
        this.currentStreak = current;
        this.longestStreak = longest;
        this.totalDays = total;
        this.lastCheckedDate = lastCheckedDate;
    }
}
