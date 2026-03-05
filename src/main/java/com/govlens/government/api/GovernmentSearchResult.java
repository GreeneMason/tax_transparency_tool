package com.govlens.government.api;

public record GovernmentSearchResult(
        String unitId,
        String unitName,
        String countyName,
        String stateAbbrev,
        String stateName,
        String govTypeCode,
        String govTypeDescription,
        Long population
) {
}
