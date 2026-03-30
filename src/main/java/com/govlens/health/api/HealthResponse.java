package com.govlens.health.api;

/** Health-check response object returned by the health endpoint. */

import java.time.Instant;

public record HealthResponse(
        String status,
        String database,
        Instant timestamp
) {
}
