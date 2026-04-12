package com.habit.domain.badge;

import com.habit.common.exception.BusinessException;
import com.habit.common.exception.ErrorCode;
import com.habit.domain.habit.CheckIn;
import com.habit.domain.habit.CheckInRepository;
import com.habit.domain.habit.Habit;
import com.habit.domain.habit.HabitRepository;
import com.habit.domain.habit.Streak;
import com.habit.domain.habit.StreakRepository;
import com.habit.infra.cache.BadgeCacheService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicBadgeService {

    private final SharedBadgeRepository sharedBadgeRepository;
    private final HabitRepository habitRepository;
    private final CheckInRepository checkInRepository;
    private final StreakRepository streakRepository;
    private final BadgeCacheService badgeCache;
    private final CalendarSvgGenerator calendarGenerator;
    private final StreakBadgeGenerator streakGenerator;
    private final TotalBadgeGenerator totalGenerator;

    @Transactional
    public String renderCalendar(String token, Map<String, String> params) {
        Context ctx = resolve(token);
        String key = badgeCache.keyFor(token, "calendar", params);
        String svg = badgeCache.computeIfAbsent(key, "calendar", () -> {
            LocalDate today = LocalDate.now();
            LocalDate windowStart = today.minusDays(364);
            List<CheckIn> checkIns = checkInRepository.findAllByHabitIdAndCheckedDateBetween(
                ctx.habit.getId(), windowStart, today);
            List<LocalDate> checkedDates = checkIns.stream().map(CheckIn::getCheckedDate).toList();

            String theme = params.getOrDefault("theme", "light");
            String fillColor = resolveColor(params.get("color"), ctx.habit.getColor());
            CalendarOptions options = "dark".equalsIgnoreCase(theme)
                ? CalendarOptions.dark(fillColor)
                : CalendarOptions.light(fillColor);
            return calendarGenerator.generate(checkedDates, today, options);
        });
        recordView(token);
        return svg;
    }

    @Transactional
    public String renderStreak(String token, Map<String, String> params) {
        Context ctx = resolve(token);
        String key = badgeCache.keyFor(token, "streak", params);
        String svg = badgeCache.computeIfAbsent(key, "streak", () -> {
            Streak streak = streakRepository.findByHabitId(ctx.habit.getId())
                .orElseGet(() -> Streak.initFor(ctx.habit.getId()));
            StreakBadgeOptions options = new StreakBadgeOptions(null, lang(params), theme(params), minWidth(params));
            return streakGenerator.generate(streak.getCurrentStreak(), options);
        });
        recordView(token);
        return svg;
    }

    @Transactional
    public String renderTotal(String token, Map<String, String> params) {
        Context ctx = resolve(token);
        String key = badgeCache.keyFor(token, "total", params);
        String svg = badgeCache.computeIfAbsent(key, "total", () -> {
            Streak streak = streakRepository.findByHabitId(ctx.habit.getId())
                .orElseGet(() -> Streak.initFor(ctx.habit.getId()));
            TotalBadgeOptions options = new TotalBadgeOptions(null, lang(params), theme(params), minWidth(params));
            return totalGenerator.generate(streak.getTotalDays(), options);
        });
        recordView(token);
        return svg;
    }

    private Context resolve(String token) {
        SharedBadge sb = sharedBadgeRepository.findByPublicToken(token)
            .orElseThrow(() -> new BusinessException(ErrorCode.BADGE_TOKEN_NOT_FOUND));
        Habit habit = habitRepository.findById(sb.getHabitId())
            .orElseThrow(() -> new BusinessException(ErrorCode.HABIT_NOT_FOUND));
        if (!habit.isPublic()) {
            throw new BusinessException(ErrorCode.BADGE_HABIT_NOT_PUBLIC);
        }
        return new Context(sb, habit);
    }

    private void recordView(String token) {
        try {
            sharedBadgeRepository.incrementViewCount(token);
        } catch (RuntimeException e) {
            log.debug("failed to increment view count for token={}", token, e);
        }
    }

    private static String resolveColor(String input, String fallback) {
        if (input == null || input.isBlank()) return fallback;
        String hex = input.startsWith("#") ? input.substring(1) : input;
        if (hex.length() != 6) return fallback;
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok) return fallback;
        }
        return "#" + hex.toLowerCase();
    }

    private static String lang(Map<String, String> params) {
        String v = params.get("lang");
        return "ko".equalsIgnoreCase(v) ? "ko" : "en";
    }

    private static String theme(Map<String, String> params) {
        String v = params.get("theme");
        return "dark".equalsIgnoreCase(v) ? "dark" : "light";
    }

    private static int minWidth(Map<String, String> params) {
        String v = params.get("width");
        if (v == null) return 0;
        try {
            return Math.max(Integer.parseInt(v), 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record Context(SharedBadge sharedBadge, Habit habit) {}
}
