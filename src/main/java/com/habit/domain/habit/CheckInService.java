package com.habit.domain.habit;

import com.habit.common.exception.BusinessException;
import com.habit.common.exception.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class CheckInService {

    private static final Duration DEDUP_TTL = Duration.ofMinutes(2);

    private final HabitRepository habitRepository;
    private final CheckInRepository checkInRepository;
    private final StreakRepository streakRepository;
    private final StringRedisTemplate redis;
    private final Counter checkInCounter;

    public CheckInService(
        HabitRepository habitRepository,
        CheckInRepository checkInRepository,
        StreakRepository streakRepository,
        StringRedisTemplate redis,
        MeterRegistry meterRegistry
    ) {
        this.habitRepository = habitRepository;
        this.checkInRepository = checkInRepository;
        this.streakRepository = streakRepository;
        this.redis = redis;
        this.checkInCounter = meterRegistry.counter("habit.checkin.total");
    }

    @Transactional
    public CheckInResult checkIn(Long userId, Long habitId, LocalDate checkedDate, String note, Mood mood) {
        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.HABIT_NOT_FOUND));

        if (!habit.isActiveOn(checkedDate)) {
            throw new BusinessException(ErrorCode.CHECKIN_OUT_OF_RANGE);
        }

        String dedupKey = "habit:checkin:" + habitId + ":" + checkedDate;
        Boolean acquired = redis.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_TTL);
        if (Boolean.FALSE.equals(acquired)) {
            throw new BusinessException(ErrorCode.CHECKIN_ALREADY_EXISTS);
        }

        try {
            if (checkInRepository.existsByHabitIdAndCheckedDate(habitId, checkedDate)) {
                throw new BusinessException(ErrorCode.CHECKIN_ALREADY_EXISTS);
            }
            CheckIn saved = checkInRepository.save(
                CheckIn.create(habitId, userId, checkedDate, note, mood)
            );
            Streak streak = streakRepository.findByHabitId(habitId)
                .orElseGet(() -> streakRepository.save(Streak.initFor(habitId)));
            streak.applyCheckIn(checkedDate);
            checkInCounter.increment();
            return new CheckInResult(saved, streak);
        } catch (DataIntegrityViolationException e) {
            redis.delete(dedupKey);
            throw new BusinessException(ErrorCode.CHECKIN_ALREADY_EXISTS);
        } catch (RuntimeException e) {
            redis.delete(dedupKey);
            throw e;
        }
    }

    public record CheckInResult(CheckIn checkIn, Streak streak) {}
}
