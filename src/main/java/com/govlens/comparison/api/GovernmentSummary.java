package com.govlens.comparison.api;

/** Lightweight government profile used in comparison responses. */

public record GovernmentSummary(
        String unitId,
        String unitName,
        String countyName,
        String govTypeDescription,
        Long population
) {
}
