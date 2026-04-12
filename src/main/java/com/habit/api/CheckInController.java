package com.habit.api;

import com.habit.api.CheckInDtos.CheckInCreateResponse;
import com.habit.api.CheckInDtos.CheckInRequest;
import com.habit.api.CheckInDtos.CheckInResponse;
import com.habit.api.CheckInDtos.CheckInUpdateRequest;
import com.habit.api.CheckInDtos.StreakSnapshot;
import com.habit.domain.habit.CheckInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/habits/{habitId}/check-ins")
@RequiredArgsConstructor
@Tag(name = "Check-Ins")
public class CheckInController {

    private final CheckInService checkInService;

    @PostMapping
    @Operation(summary = "체크인 생성", description = "특정 날짜(기본 오늘 UTC)에 대한 체크인을 기록합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "생성됨"),
        @ApiResponse(responseCode = "400", description = "CHECKIN_OUT_OF_RANGE"),
        @ApiResponse(responseCode = "404", description = "HABIT_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "CHECKIN_ALREADY_EXISTS")
    })
    public ResponseEntity<CheckInCreateResponse> create(
        @PathVariable Long habitId,
        @AuthenticationPrincipal Long userId,
        @RequestBody @Valid CheckInRequest req
    ) {
        LocalDate date = req.checkedDate() != null ? req.checkedDate() : LocalDate.now(ZoneOffset.UTC);
        CheckInService.CheckInResult result = checkInService.checkIn(userId, habitId, date, req.note(), req.mood());
        CheckInCreateResponse body = new CheckInCreateResponse(
            CheckInResponse.of(result.checkIn()),
            StreakSnapshot.of(result.streak())
        );
        URI location = URI.create("/api/v1/habits/" + habitId + "/check-ins/" + date);
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping
    @Operation(summary = "체크인 목록 조회", description = "지정된 날짜 범위의 체크인을 반환합니다. 기본 범위: [오늘-29, 오늘] UTC.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "HABIT_NOT_FOUND")
    })
    public List<CheckInResponse> list(
        @PathVariable Long habitId,
        @AuthenticationPrincipal Long userId,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to
    ) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate effectiveTo = to != null ? to : today;
        LocalDate effectiveFrom = from != null ? from : today.minusDays(29);
        return checkInService.list(userId, habitId, effectiveFrom, effectiveTo)
            .stream()
            .map(CheckInResponse::of)
            .toList();
    }

    @PatchMapping("/{date}")
    @Operation(summary = "체크인 메모/기분 수정")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "404", description = "습관 또는 체크인을 찾을 수 없음")
    })
    public CheckInResponse update(
        @PathVariable Long habitId,
        @PathVariable LocalDate date,
        @AuthenticationPrincipal Long userId,
        @RequestBody @Valid CheckInUpdateRequest req
    ) {
        return CheckInResponse.of(
            checkInService.updateNoteAndMood(userId, habitId, date, req.note(), req.mood())
        );
    }
}
