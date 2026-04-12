package com.habit.domain.stats;

import com.habit.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(
        name = "habit_stats",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_habit_stats_habit_date", columnNames = {"habit_id", "stats_date"})
        },
        indexes = {
                @Index(name = "ix_habit_stats_habit_month", columnList = "habit_id, month_number")
        }
)
public class HabitStats extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "habit_id", nullable = false)
    private Long habitId;

    @Column(name = "stats_date", nullable = false)
    private LocalDate statsDate;

    @Column(name = "week_number", nullable = false)
    private int weekNumber;

    @Column(name = "month_number", nullable = false)
    private int monthNumber;

    @Column(name = "is_checked", nullable = false)
    private boolean isChecked;

    public static HabitStats of(Long habitId, LocalDate date, boolean isChecked) {
        int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        int month = date.getMonthValue();
        return HabitStats.builder()
                .habitId(habitId)
                .statsDate(date)
                .weekNumber(week)
                .monthNumber(month)
                .isChecked(isChecked)
                .build();
    }
}
