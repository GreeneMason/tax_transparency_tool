package com.govlens.comparison.api;

public record GovernmentSummary(
        String unitId,
        String unitName,
        String countyName,
        String govTypeDescription,
        Long population
) {
}
