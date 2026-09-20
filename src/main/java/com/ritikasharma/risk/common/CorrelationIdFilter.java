package com.ritikasharma.risk.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String value = request.getHeader(HEADER);
        if (value == null || value.isBlank() || value.length() > 64) value = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, value);
        response.setHeader(HEADER, value);
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", value)) {
            chain.doFilter(request, response);
        }
    }
}
