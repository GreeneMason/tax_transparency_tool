package com.govlens.comparison.api;

import java.util.List;

public record ComparisonResponse(
        Integer year,
        GovernmentSummary leftGovernment,
        GovernmentSummary rightGovernment,
        List<ComparisonItem> items
) {
}
