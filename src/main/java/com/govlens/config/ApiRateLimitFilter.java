package com.govlens.config;

/** Servlet filter that applies request rate limits to API traffic. */

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    @Value("${govlens.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${govlens.rate-limit.requests-per-window:120}")
    private int requestsPerWindow;

    @Value("${govlens.rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Value("${govlens.rate-limit.max-tracked-clients:10000}")
    private int maxTrackedClients;

    private final Map<String, ClientWindow> clientWindows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled || requestsPerWindow <= 0 || windowSeconds <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;
        String clientKey = resolveClientKey(request);

        ClientWindow currentWindow = clientWindows.compute(clientKey, (key, existing) -> {
            if (existing == null || now - existing.windowStartMillis >= windowMillis) {
                return new ClientWindow(now, 1);
            }
            return new ClientWindow(existing.windowStartMillis, existing.requestCount + 1);
        });

        if (currentWindow != null && currentWindow.requestCount > requestsPerWindow) {
            long elapsed = now - currentWindow.windowStartMillis;
            long retryAfterSeconds = Math.max(1L, (windowMillis - elapsed + 999L) / 1000L);

            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Try again later.\"}");
            return;
        }

        cleanupStaleEntries(now, windowMillis);
        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int commaIndex = forwardedFor.indexOf(',');
            String firstIp = commaIndex >= 0 ? forwardedFor.substring(0, commaIndex) : forwardedFor;
            String normalized = firstIp.trim();
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }

        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null ? "unknown" : remoteAddr;
    }

    private void cleanupStaleEntries(long now, long windowMillis) {
        if (clientWindows.size() <= maxTrackedClients) {
            return;
        }

        long threshold = now - windowMillis;
        clientWindows.entrySet().removeIf(entry -> entry.getValue().windowStartMillis < threshold);

        if (clientWindows.size() <= maxTrackedClients) {
            return;
        }

        int targetSize = Math.max(1, maxTrackedClients);
        int removed = 0;
        for (String key : clientWindows.keySet()) {
            clientWindows.remove(key);
            removed++;
            if (clientWindows.size() <= targetSize || removed >= 500) {
                break;
            }
        }
    }

    private static final class ClientWindow {
        private final long windowStartMillis;
        private final int requestCount;

        private ClientWindow(long windowStartMillis, int requestCount) {
            this.windowStartMillis = windowStartMillis;
            this.requestCount = requestCount;
        }
    }
}
