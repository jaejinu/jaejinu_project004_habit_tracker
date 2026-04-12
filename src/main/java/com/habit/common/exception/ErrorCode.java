package com.habit.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_400", "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_403", "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "COMMON_409", "요청이 현재 상태와 충돌합니다."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "COMMON_429", "요청 한도를 초과했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404", "사용자를 찾을 수 없습니다."),
    USER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "USER_409_EMAIL", "이미 사용 중인 이메일입니다."),

    HABIT_NOT_FOUND(HttpStatus.NOT_FOUND, "HABIT_404", "습관을 찾을 수 없습니다."),
    HABIT_FORBIDDEN(HttpStatus.FORBIDDEN, "HABIT_403", "해당 습관에 접근할 권한이 없습니다."),

    CHECKIN_ALREADY_EXISTS(HttpStatus.CONFLICT, "CHECKIN_409", "오늘은 이미 체크인했습니다."),
    CHECKIN_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "CHECKIN_400_RANGE", "체크인 가능한 날짜 범위가 아닙니다."),

    BADGE_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "BADGE_404", "뱃지 토큰을 찾을 수 없습니다."),
    BADGE_HABIT_NOT_PUBLIC(HttpStatus.FORBIDDEN, "BADGE_403", "비공개 습관의 뱃지는 생성할 수 없습니다."),
    BADGE_INVALID_PARAM(HttpStatus.BAD_REQUEST, "BADGE_400", "뱃지 요청 파라미터가 올바르지 않습니다."),

    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_401_CRED", "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_401_TOKEN", "토큰이 유효하지 않습니다."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_401_EXPIRED", "토큰이 만료되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;
}
