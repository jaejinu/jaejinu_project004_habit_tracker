package com.habit.domain.habit;

import com.habit.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
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
@Table(name = "habits", indexes = {@Index(name = "ix_habits_user_id", columnList = "user_id")})
public class Habit extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "color", nullable = false, length = 7)
    private String color;

    @Column(name = "icon", length = 16)
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private HabitFrequency frequency;

    @Column(name = "target_days")
    private Integer targetDays;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    public static Habit create(Long userId, String title, HabitFrequency frequency, LocalDate startDate) {
        return Habit.builder()
                .userId(userId)
                .title(title)
                .frequency(frequency)
                .startDate(startDate)
                .color("#216e39")
                .isPublic(false)
                .build();
    }

    public void updateBasic(String title, String description, String color, String icon) {
        this.title = title;
        this.description = description;
        this.color = color;
        this.icon = icon;
    }

    public void togglePublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public boolean isActiveOn(LocalDate date) {
        return !startDate.isAfter(date) && (endDate == null || !date.isAfter(endDate));
    }
}
