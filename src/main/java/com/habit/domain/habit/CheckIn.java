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
import jakarta.persistence.UniqueConstraint;
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
@Table(
    name = "check_ins",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_check_ins_habit_date", columnNames = {"habit_id", "checked_date"})
    },
    indexes = {
        @Index(name = "ix_check_ins_user_date", columnList = "user_id, checked_date"),
        @Index(name = "ix_check_ins_habit_date", columnList = "habit_id, checked_date")
    }
)
public class CheckIn extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "habit_id", nullable = false)
    private Long habitId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "checked_date", nullable = false)
    private LocalDate checkedDate;

    @Column(name = "note", length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "mood")
    private Mood mood;

    public static CheckIn create(Long habitId, Long userId, LocalDate checkedDate, String note, Mood mood) {
        return CheckIn.builder()
            .habitId(habitId)
            .userId(userId)
            .checkedDate(checkedDate)
            .note(note)
            .mood(mood)
            .build();
    }

    public void updateNote(String note, Mood mood) {
        this.note = note;
        this.mood = mood;
    }
}
