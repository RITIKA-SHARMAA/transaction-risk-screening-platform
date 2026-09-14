package com.ritikasharma.risk.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * RFC 7807 responses for errors raised inside controllers. Spring MVC's own exceptions (validation,
 * unreadable body, unsupported media type, ...) are handled by the {@link ResponseEntityExceptionHandler} base.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** Failed login. One message for every cause so the response does not reveal whether the user exists. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return ProblemDetails.of(HttpStatus.UNAUTHORIZED, "Invalid username or password.", request);
    }
}
