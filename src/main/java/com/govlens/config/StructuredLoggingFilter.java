package com.govlens.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * Structured request logging filter.
 * Adds request ID (trace ID) and logs latency for all API requests.
 */
@Component
public class StructuredLoggingFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(StructuredLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_MDC = "request_id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(REQUEST_ID_MDC, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long startTime = System.nanoTime();
        String method = request.getMethod();
        String path = request.getRequestURI();
        String query = request.getQueryString();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long endTime = System.nanoTime();
            long latencyMs = (endTime - startTime) / 1_000_000;
            int statusCode = response.getStatus();

            logger.info(
                "endpoint={} method={} path={} query={} status={} latency_ms={}",
                path, method, path, query != null ? query : "", statusCode, latencyMs
            );

            MDC.remove(REQUEST_ID_MDC);
        }
    }
}
