package com.ritikasharma.risk.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritikasharma.risk.common.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 for a missing, malformed, expired or badly signed token. The delegate sets the RFC 6750
 * {@code WWW-Authenticate} header; this class adds an RFC 7807 body.
 */
@Component
class ProblemDetailsAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();
    private final ObjectMapper objectMapper;

    ProblemDetailsAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        delegate.commence(request, response, exception);
        String detail = exception instanceof InvalidBearerTokenException
                ? "The bearer token is malformed, expired or has an invalid signature."
                : "Authentication is required to access this resource.";
        ProblemDetails.write(response, objectMapper, ProblemDetails.of(HttpStatus.UNAUTHORIZED, detail, request));
    }
}
