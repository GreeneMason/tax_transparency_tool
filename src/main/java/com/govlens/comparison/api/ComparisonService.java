package com.govlens.comparison.api;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ComparisonService {

    private final ComparisonRepository repository;

    public ComparisonService(ComparisonRepository repository) {
        this.repository = repository;
    }

    public ComparisonResponse compare(String leftUnitId, String rightUnitId, Integer year) {
        String left = normalizeUnitId(leftUnitId, "leftUnitId");
        String right = normalizeUnitId(rightUnitId, "rightUnitId");
        int normalizedYear = normalizeYear(year);

        if (left.equals(right)) {
            throw new IllegalArgumentException("leftUnitId and rightUnitId must be different.");
        }

        GovernmentSummary leftGovernment = repository.findWashingtonGovernment(left)
                .orElseThrow(() -> new IllegalArgumentException("leftUnitId not found in Washington dataset."));

        GovernmentSummary rightGovernment = repository.findWashingtonGovernment(right)
                .orElseThrow(() -> new IllegalArgumentException("rightUnitId not found in Washington dataset."));

        List<ComparisonItem> items = repository.compareByItemCode(left, right, normalizedYear);

        return new ComparisonResponse(normalizedYear, leftGovernment, rightGovernment, items);
    }

    private static String normalizeUnitId(String raw, String parameterName) {
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.length() != 12) {
            throw new IllegalArgumentException(parameterName + " must be exactly 12 characters.");
        }
        return normalized;
    }

    private static int normalizeYear(Integer year) {
        if (year == null) {
            throw new IllegalArgumentException("year is required.");
        }
        if (year < 1900 || year > 2100) {
            throw new IllegalArgumentException("year must be between 1900 and 2100.");
        }
        return year;
    }
}
