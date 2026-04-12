package com.habit.infra.scheduler;

import com.habit.domain.habit.CheckInRepository;
import com.habit.domain.habit.Habit;
import com.habit.domain.habit.HabitRepository;
import com.habit.domain.stats.HabitStatsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class DailyStatsAggregationJob {

    private final HabitRepository habitRepository;
    private final CheckInRepository checkInRepository;
    private final HabitStatsService habitStatsService;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "0 5 2 * * *", zone = "UTC")
    @Transactional
    public void run() {
        Timer.Sample sample = Timer.Sample.start(meterRegistry);
        try {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            LocalDate yesterday = today.minusDays(1);

            Counter counter = meterRegistry.counter("habit.stats.aggregation.rows");

            List<Habit> activeHabits = habitRepository.findAllActiveOn(yesterday);
            int total = activeHabits.size();
            int inserted = 0;

            // TODO: paginate when habit count > 10k
            for (Habit habit : activeHabits) {
                boolean checked = checkInRepository.existsByHabitIdAndCheckedDate(habit.getId(), yesterday);
                boolean wasInserted = habitStatsService.recordFor(habit.getId(), yesterday, checked);
                if (wasInserted) {
                    counter.increment();
                    inserted++;
                }
            }

            log.info("daily stats aggregation complete: date={} active_habits={} inserted={}", yesterday, total, inserted);
        } finally {
            sample.stop(meterRegistry.timer("habit.stats.aggregation.duration"));
        }
    }
}
