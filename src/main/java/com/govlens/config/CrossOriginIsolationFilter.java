package com.govlens.config;

/**
 * Adds Cross-Origin isolation headers required for SharedArrayBuffer,
 * which sql.js-httpvfs needs for HTTP Range Request reads of the SQLite map database.
 *
 * Required headers (Chrome 92+, Firefox 79+):
 *   Cross-Origin-Opener-Policy:   same-origin
 *   Cross-Origin-Embedder-Policy: require-corp
 *
 * Applied only to /map.html to avoid breaking other pages or API clients.
 */

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class CrossOriginIsolationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("/map.html".equals(path) || "/map".equals(path)) {
            response.setHeader("Cross-Origin-Opener-Policy",   "same-origin");
            response.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
        }
        filterChain.doFilter(request, response);
    }
}
