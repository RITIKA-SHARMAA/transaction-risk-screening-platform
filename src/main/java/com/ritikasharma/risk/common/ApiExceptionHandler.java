package com.ritikasharma.risk.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return ProblemDetails.of(HttpStatus.UNAUTHORIZED, "Invalid username or password.", request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail handleConflict(IdempotencyConflictException exception, HttpServletRequest request) {
        return ProblemDetails.of(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst().orElse("Request validation failed.");
        return ProblemDetails.of(HttpStatus.BAD_REQUEST, detail, request);
    }
}
