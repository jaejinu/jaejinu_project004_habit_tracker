package com.habit.infra.scheduler;

import com.habit.domain.habit.Habit;
import com.habit.domain.habit.HabitRepository;
import com.habit.domain.habit.StreakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class WeeklyStreakRecalcJob {

    private final HabitRepository habitRepository;
    private final StreakService streakService;

    @Scheduled(cron = "0 0 3 * * SUN", zone = "UTC")
    public void run() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Habit> habits = habitRepository.findAllActiveOn(today);
        int total = 0;
        int errors = 0;
        // TODO: paginate when habit count > 10k
        for (Habit habit : habits) {
            try {
                streakService.recalculate(habit.getId(), today);
                total++;
            } catch (RuntimeException e) {
                errors++;
                log.warn("weekly streak recalc failed: habitId={}", habit.getId(), e);
            }
        }
        log.info("weekly streak recalc complete: date={} processed={} errors={}", today, total, errors);
    }
}
