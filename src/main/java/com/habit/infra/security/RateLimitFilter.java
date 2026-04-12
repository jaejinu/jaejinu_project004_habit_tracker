package com.habit.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.habit.common.exception.ErrorCode;
import com.habit.common.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final DateTimeFormatter BUCKET_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private static final String[] EXEMPT_PREFIXES = {
        "/api/v1/badges/",
        "/actuator/",
        "/swagger-ui",
        "/v3/api-docs"
    };

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        for (String prefix : EXEMPT_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        String scope;
        String id;
        int limit;

        Long authenticatedUserId = resolveAuthenticatedUserId();
        if (authenticatedUserId != null) {
            scope = "user";
            id = String.valueOf(authenticatedUserId);
            limit = properties.getAuthenticatedPerHour();
        } else {
            scope = "ip";
            id = resolveClientIp(request);
            limit = properties.getAnonymousPerHour();
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String bucket = BUCKET_FORMATTER.format(now);
        String key = "rate:" + scope + ":" + id + ":" + bucket;

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofHours(1));
        }

        if (count != null && count > limit) {
            long retryAfter = secondsUntilNextHour(now);
            log.warn("Rate limit exceeded scope={} id={} count={} limit={}", scope, id, count, limit);
            writeRateLimitResponse(request, response, retryAfter);
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeRateLimitResponse(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
        throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));

        ErrorResponse body = ErrorResponse.of(ErrorCode.RATE_LIMIT_EXCEEDED, request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int commaIdx = forwarded.indexOf(',');
            String first = commaIdx >= 0 ? forwarded.substring(0, commaIdx) : forwarded;
            return first.trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote.trim();
    }

    private Long resolveAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        return null;
    }

    private long secondsUntilNextHour(ZonedDateTime now) {
        ZonedDateTime nextHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        long seconds = Duration.between(now, nextHour).getSeconds();
        return Math.max(seconds, 1);
    }
}
