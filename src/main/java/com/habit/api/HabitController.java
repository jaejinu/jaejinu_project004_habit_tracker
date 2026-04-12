package com.habit.api;

import com.habit.api.HabitDtos.HabitCreateRequest;
import com.habit.api.HabitDtos.HabitResponse;
import com.habit.api.HabitDtos.HabitUpdateRequest;
import com.habit.api.HabitDtos.VisibilityRequest;
import com.habit.domain.habit.Habit;
import com.habit.domain.habit.HabitService;
import com.habit.domain.habit.Streak;
import com.habit.domain.habit.StreakRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/habits")
@RequiredArgsConstructor
@Tag(name = "Habits")
public class HabitController {

    private final HabitService habitService;
    private final StreakRepository streakRepository;

    @GetMapping
    @Operation(summary = "내 습관 목록", description = "인증된 사용자의 모든 습관을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    public List<HabitResponse> list(@AuthenticationPrincipal Long userId) {
        return habitService.listMine(userId).stream()
            .map(h -> toResponse(h))
            .toList();
    }

    @GetMapping("/{habitId}")
    @Operation(summary = "습관 상세 조회", description = "단일 습관과 현재 스트릭을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "습관을 찾을 수 없음")
    public HabitResponse get(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long habitId
    ) {
        Habit habit = habitService.get(userId, habitId);
        return toResponse(habit);
    }

    @PostMapping
    @Operation(summary = "습관 생성", description = "새 습관을 생성합니다.")
    @ApiResponse(responseCode = "201", description = "생성됨")
    @ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음")
    public ResponseEntity<HabitResponse> create(
        @AuthenticationPrincipal Long userId,
        @RequestBody @Valid HabitCreateRequest req
    ) {
        Habit habit = habitService.create(userId, req.title(), req.frequency(), req.startDate());
        if (req.description() != null || req.color() != null || req.icon() != null) {
            String color = req.color() != null ? req.color() : habit.getColor();
            habit = habitService.updateBasic(
                userId,
                habit.getId(),
                req.title(),
                req.description(),
                color,
                req.icon()
            );
        }
        if (req.targetDays() != null || req.endDate() != null) {
            habit = habitService.updateSchedule(userId, habit.getId(), req.targetDays(), req.endDate());
        }
        HabitResponse body = toResponse(habit);
        return ResponseEntity
            .created(URI.create("/api/v1/habits/" + habit.getId()))
            .body(body);
    }

    @PatchMapping("/{habitId}")
    @Operation(summary = "습관 수정", description = "title/description/color/icon 을 부분 수정합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "습관을 찾을 수 없음")
    public HabitResponse update(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long habitId,
        @RequestBody @Valid HabitUpdateRequest req
    ) {
        Habit current = habitService.get(userId, habitId);
        String title = req.title() != null ? req.title() : current.getTitle();
        String description = req.description() != null ? req.description() : current.getDescription();
        String color = req.color() != null ? req.color() : current.getColor();
        String icon = req.icon() != null ? req.icon() : current.getIcon();
        Habit updated = habitService.updateBasic(userId, habitId, title, description, color, icon);
        if (req.targetDays() != null || req.endDate() != null) {
            Integer targetDays = req.targetDays() != null ? req.targetDays() : current.getTargetDays();
            LocalDate endDate = req.endDate() != null ? req.endDate() : current.getEndDate();
            updated = habitService.updateSchedule(userId, habitId, targetDays, endDate);
        }
        return toResponse(updated);
    }

    @PatchMapping("/{habitId}/visibility")
    @Operation(summary = "공개 여부 토글", description = "isPublic 플래그를 설정합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "습관을 찾을 수 없음")
    public HabitResponse visibility(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long habitId,
        @RequestBody @Valid VisibilityRequest req
    ) {
        Habit habit = habitService.togglePublic(userId, habitId, req.isPublic());
        return toResponse(habit);
    }

    @DeleteMapping("/{habitId}")
    @Operation(summary = "습관 삭제", description = "습관과 관련된 체크인/스트릭/통계/뱃지를 모두 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "삭제됨")
    @ApiResponse(responseCode = "404", description = "습관을 찾을 수 없음")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long habitId
    ) {
        habitService.delete(userId, habitId);
        return ResponseEntity.noContent().build();
    }

    private HabitResponse toResponse(Habit h) {
        Streak streak = streakRepository.findByHabitId(h.getId())
            .orElseGet(() -> Streak.initFor(h.getId()));
        return HabitResponse.of(h, streak.getCurrentStreak(), streak.getLongestStreak(), streak.getTotalDays());
    }
}
