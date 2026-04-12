package com.habit.common.exception;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
    String code,
    String message,
    Instant timestamp,
    String path,
    List<FieldError> errors
) {
    public record FieldError(String field, String reason) {}

    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getDefaultMessage(), Instant.now(), path, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return new ErrorResponse(errorCode.getCode(), message, Instant.now(), path, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String path, List<FieldError> fieldErrors) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getDefaultMessage(), Instant.now(), path, fieldErrors);
    }
}
