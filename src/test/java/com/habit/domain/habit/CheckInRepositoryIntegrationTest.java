package com.habit.domain.habit;

import static org.assertj.core.api.Assertions.assertThat;

import com.habit.infra.persistence.JpaConfig;
import com.habit.support.AbstractPostgresIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Verifies the PostgreSQL LAG-based gap-and-island streak queries by hitting
 * a real Postgres 16 container. H2 cannot reproduce the exact window-function
 * semantics, so this suite uses Testcontainers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class CheckInRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Long HABIT_ID = 42L;
    private static final Long USER_ID = 7L;

    @Autowired
    private CheckInRepository checkInRepository;

    @BeforeEach
    void clean() {
        checkInRepository.deleteAllByHabitId(HABIT_ID);
    }

    @Test
    @DisplayName("recalcCurrentStreak returns 0 when there are no check-ins")
    void currentStreak_empty() {
        int streak = checkInRepository.recalcCurrentStreak(HABIT_ID, LocalDate.of(2026, 4, 12));
        assertThat(streak).isZero();
    }

    @Test
    @DisplayName("recalcCurrentStreak counts contiguous days ending at today")
    void currentStreak_contiguous_today() {
        LocalDate today = LocalDate.of(2026, 4, 12);
        save(today.minusDays(4));
        save(today.minusDays(3));
        save(today.minusDays(2));
        save(today.minusDays(1));
        save(today);

        int streak = checkInRepository.recalcCurrentStreak(HABIT_ID, today);

        assertThat(streak).isEqualTo(5);
    }

    @Test
    @DisplayName("recalcCurrentStreak accepts a streak ending yesterday (user hasn't checked in yet today)")
    void currentStreak_ending_yesterday() {
        LocalDate today = LocalDate.of(2026, 4, 12);
        save(today.minusDays(3));
        save(today.minusDays(2));
        save(today.minusDays(1));

        int streak = checkInRepository.recalcCurrentStreak(HABIT_ID, today);

        assertThat(streak).isEqualTo(3);
    }

    @Test
    @DisplayName("recalcCurrentStreak returns 0 when the latest check-in is 2+ days old")
    void currentStreak_broken() {
        LocalDate today = LocalDate.of(2026, 4, 12);
        save(today.minusDays(5));
        save(today.minusDays(4));
        save(today.minusDays(3));

        int streak = checkInRepository.recalcCurrentStreak(HABIT_ID, today);

        assertThat(streak).isZero();
    }

    @Test
    @DisplayName("recalcLongestStreak returns the max contiguous run across history")
    void longestStreak_across_history() {
        LocalDate anchor = LocalDate.of(2026, 4, 12);
        save(anchor.minusDays(20));
        save(anchor.minusDays(19));
        save(anchor.minusDays(18));
        save(anchor.minusDays(17));
        save(anchor.minusDays(16));
        save(anchor.minusDays(15));
        save(anchor.minusDays(3));
        save(anchor.minusDays(2));
        save(anchor.minusDays(1));

        int longest = checkInRepository.recalcLongestStreak(HABIT_ID);

        assertThat(longest).isEqualTo(6);
    }

    @Test
    @DisplayName("recalcLongestStreak returns 0 for empty history")
    void longestStreak_empty() {
        int longest = checkInRepository.recalcLongestStreak(HABIT_ID);
        assertThat(longest).isZero();
    }

    @Test
    @DisplayName("unique constraint (habit_id, checked_date) rejects duplicate insert")
    void unique_constraint_rejects_duplicate() {
        LocalDate date = LocalDate.of(2026, 4, 12);
        save(date);

        boolean exists = checkInRepository.existsByHabitIdAndCheckedDate(HABIT_ID, date);
        assertThat(exists).isTrue();
    }

    private void save(LocalDate date) {
        checkInRepository.save(CheckIn.create(HABIT_ID, USER_ID, date, null, null));
    }
}
