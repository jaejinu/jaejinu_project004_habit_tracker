package com.habit.api;

import com.habit.domain.badge.BadgeType;
import com.habit.domain.badge.SharedBadge;
import com.habit.domain.badge.SharedBadgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/habits/{habitId}/badges")
@RequiredArgsConstructor
@Tag(name = "Badges (Issue)", description = "뱃지 토큰 발급 — 인증 필요")
public class SharedBadgeController {

    private final SharedBadgeService sharedBadgeService;

    @PostMapping
    @Operation(summary = "뱃지 토큰 발급", description = "현재 사용자의 공개 습관에 대해 SharedBadge 토큰을 생성합니다.")
    @ApiResponse(responseCode = "403", description = "해당 습관의 소유자가 아니거나 비공개 습관")
    public ResponseEntity<IssueResponse> issue(
        @PathVariable Long habitId,
        @AuthenticationPrincipal Long userId,
        @RequestBody @Valid IssueRequest request
    ) {
        SharedBadge badge = sharedBadgeService.issue(userId, habitId, request.type());
        String suffix = switch (badge.getBadgeType()) {
            case CALENDAR -> "calendar.svg";
            case STREAK -> "streak.svg";
            case TOTAL -> "total.svg";
        };
        String url = "/api/v1/badges/" + badge.getPublicToken() + "/" + suffix;
        return ResponseEntity.ok(new IssueResponse(badge.getPublicToken(), url));
    }

    public record IssueRequest(@NotNull BadgeType type) {}

    public record IssueResponse(String token, String url) {}
}
