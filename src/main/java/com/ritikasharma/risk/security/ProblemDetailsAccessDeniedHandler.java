package com.ritikasharma.risk.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritikasharma.risk.common.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 403 for an authenticated caller without the required role, with an RFC 7807 body. */
@Component
class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {

    private final BearerTokenAccessDeniedHandler delegate = new BearerTokenAccessDeniedHandler();
    private final ObjectMapper objectMapper;

    ProblemDetailsAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        delegate.handle(request, response, exception);
        ProblemDetails.write(response, objectMapper, ProblemDetails.of(HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource.", request));
    }
}
