package com.govlens.government.api;

/** Search result row returned by government discovery endpoints. */

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
