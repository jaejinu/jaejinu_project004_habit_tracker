package com.habit.infra.scheduler;

import com.habit.domain.habit.Streak;
import com.habit.domain.habit.StreakRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
public class StreakMaintenanceJob {

    private final StreakRepository streakRepository;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "0 10 0 * * *", zone = "UTC")
    @Transactional
    public void run() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate cutoff = today.minusDays(1);
        List<Streak> broken = streakRepository.findAllPotentiallyBroken(cutoff);
        Counter counter = meterRegistry.counter("habit.streak.broken.total");
        // TODO: paginate if streak count > 10k
        for (Streak streak : broken) {
            streak.overwrite(0, streak.getLongestStreak(), streak.getTotalDays(), streak.getLastCheckedDate());
            counter.increment();
        }
        log.info("streak maintenance: date={} reset={}", today, broken.size());
    }
}
