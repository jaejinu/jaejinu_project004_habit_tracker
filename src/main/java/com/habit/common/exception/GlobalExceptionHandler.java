package com.habit.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest req) {
        log.warn("business exception: code={} path={} message={}", ex.getErrorCode().getCode(), req.getRequestURI(), ex.getMessage());
        ErrorCode ec = ex.getErrorCode();
        return ResponseEntity.status(ec.getStatus())
            .body(ErrorResponse.of(ec, ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
            .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, req.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex, HttpServletRequest req) {
        log.error("unhandled exception at {}", req.getRequestURI(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
            .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, req.getRequestURI()));
    }
}
