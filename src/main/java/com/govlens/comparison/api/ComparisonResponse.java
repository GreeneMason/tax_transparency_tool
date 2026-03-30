package com.govlens.comparison.api;

/** API response payload containing summary and item-level comparison results. */

import java.util.List;

public record ComparisonResponse(
        Integer year,
        GovernmentSummary leftGovernment,
        GovernmentSummary rightGovernment,
        List<ComparisonItem> items
) {
}
