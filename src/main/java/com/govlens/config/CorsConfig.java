package com.govlens.config;

/** CORS policy configuration for browser access to GovLens endpoints. */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${govlens.cors.allowed-origins:http://localhost:8080,http://127.0.0.1:8080,http://localhost:8081,http://127.0.0.1:8081,http://localhost:3000,http://127.0.0.1:3000}")
    private String allowedOrigins;

    @Value("${govlens.environment:dev}")
    private String environment;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = parseAllowedOrigins();
        validateForProduction(origins);

        registry.addMapping("/api/**")
            .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);

        registry.addMapping("/health")
            .allowedOriginPatterns(origins)
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    private String[] parseAllowedOrigins() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
    }

    private void validateForProduction(String[] origins) {
        boolean productionMode = "prod".equalsIgnoreCase(environment)
                || "production".equalsIgnoreCase(environment);
        if (!productionMode) {
            return;
        }

        if (origins.length == 0) {
            throw new IllegalStateException("Production mode requires GOVLENS_CORS_ALLOWED_ORIGINS to be set.");
        }

        boolean hasWildcard = Arrays.stream(origins).anyMatch(origin -> origin.contains("*"));
        if (hasWildcard) {
            throw new IllegalStateException("Wildcard CORS origins are not allowed when GOVLENS_ENV=prod.");
        }
    }
}
