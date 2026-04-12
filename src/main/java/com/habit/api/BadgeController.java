package com.habit.api;

import com.habit.domain.badge.PublicBadgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/badges")
@RequiredArgsConstructor
@Tag(name = "Badges (Public SVG)", description = "공개 SVG 뱃지 — 인증 없이 조회 가능하며 1시간 캐시됨")
public class BadgeController {

    private static final MediaType SVG = MediaType.parseMediaType("image/svg+xml;charset=utf-8");
    private static final String CACHE_CONTROL = "public, max-age=3600, s-maxage=3600, stale-while-revalidate=600";

    private final PublicBadgeService badgeService;

    @GetMapping("/{token}/calendar.svg")
    @SecurityRequirements({})
    @Operation(summary = "53×7 잔디 캘린더 SVG", description = "최근 365일 체크인 상태를 렌더링합니다. ETag/If-None-Match 지원.")
    @ApiResponses({
        @ApiResponse(responseCode = "304", description = "If-None-Match 매치 시 본문 없음"),
        @ApiResponse(responseCode = "404", description = "토큰이 없거나 비공개 습관")
    })
    public ResponseEntity<String> calendar(
        @Parameter(description = "SharedBadge publicToken (URL-safe Base64, 32자)", example = "aB3kL9mN2pQ5rS7tU...")
        @PathVariable String token,
        @Parameter(description = "뱃지 색상 (hex, # 없이)", example = "2da44e")
        @RequestParam(required = false) String color,
        @Parameter(description = "테마 (light/dark)", example = "dark")
        @RequestParam(required = false) String theme,
        @Parameter(description = "언어 코드", example = "ko")
        @RequestParam(required = false, name = "lang") String lang,
        @Parameter(description = "뱃지 너비 (px)", example = "120")
        @RequestParam(required = false) String width,
        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        Map<String, String> params = params(color, theme, lang, width);
        String svg = badgeService.renderCalendar(token, params);
        return svgResponse(svg, ifNoneMatch);
    }

    @GetMapping("/{token}/streak.svg")
    @SecurityRequirements({})
    @Operation(summary = "연속 달성 뱃지 SVG")
    @ApiResponses({
        @ApiResponse(responseCode = "304", description = "If-None-Match 매치 시 본문 없음"),
        @ApiResponse(responseCode = "404", description = "토큰이 없거나 비공개 습관")
    })
    public ResponseEntity<String> streak(
        @Parameter(description = "SharedBadge publicToken (URL-safe Base64, 32자)", example = "aB3kL9mN2pQ5rS7tU...")
        @PathVariable String token,
        @Parameter(description = "테마 (light/dark)", example = "dark")
        @RequestParam(required = false) String theme,
        @Parameter(description = "언어 코드", example = "ko")
        @RequestParam(required = false, name = "lang") String lang,
        @Parameter(description = "뱃지 너비 (px)", example = "120")
        @RequestParam(required = false) String width,
        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        Map<String, String> params = params(null, theme, lang, width);
        String svg = badgeService.renderStreak(token, params);
        return svgResponse(svg, ifNoneMatch);
    }

    @GetMapping("/{token}/total.svg")
    @SecurityRequirements({})
    @Operation(summary = "총 달성 뱃지 SVG")
    @ApiResponses({
        @ApiResponse(responseCode = "304", description = "If-None-Match 매치 시 본문 없음"),
        @ApiResponse(responseCode = "404", description = "토큰이 없거나 비공개 습관")
    })
    public ResponseEntity<String> total(
        @Parameter(description = "SharedBadge publicToken (URL-safe Base64, 32자)", example = "aB3kL9mN2pQ5rS7tU...")
        @PathVariable String token,
        @Parameter(description = "테마 (light/dark)", example = "dark")
        @RequestParam(required = false) String theme,
        @Parameter(description = "언어 코드", example = "ko")
        @RequestParam(required = false, name = "lang") String lang,
        @Parameter(description = "뱃지 너비 (px)", example = "120")
        @RequestParam(required = false) String width,
        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        Map<String, String> params = params(null, theme, lang, width);
        String svg = badgeService.renderTotal(token, params);
        return svgResponse(svg, ifNoneMatch);
    }

    private static Map<String, String> params(String color, String theme, String lang, String width) {
        Map<String, String> p = new HashMap<>();
        if (color != null) p.put("color", color);
        if (theme != null) p.put("theme", theme);
        if (lang != null) p.put("lang", lang);
        if (width != null) p.put("width", width);
        return p;
    }

    private static ResponseEntity<String> svgResponse(String svg, String ifNoneMatch) {
        String etag = "\"" + sha1Hex16(svg) + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304)
                .header(HttpHeaders.ETAG, etag)
                .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .build();
        }
        return ResponseEntity.ok()
            .contentType(SVG)
            .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
            .header(HttpHeaders.ETAG, etag)
            .header(HttpHeaders.VARY, "Accept-Encoding")
            .body(svg);
    }

    private static String sha1Hex16(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8 && i < digest.length; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
