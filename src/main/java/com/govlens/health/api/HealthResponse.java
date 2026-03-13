package com.govlens.health.api;

import java.time.Instant;

public record HealthResponse(
        String status,
        String database,
        Instant timestamp
) {
}
