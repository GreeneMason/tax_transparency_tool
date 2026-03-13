package com.govlens.government.api;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class GovernmentSearchService {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_YEAR = 2023;
    private static final Pattern ZIP_PATTERN = Pattern.compile("^\\d{5}$");

    private final GovernmentSearchRepository repository;
    private final Map<Integer, Set<String>> incomeTaxUnitIdsByYear = new ConcurrentHashMap<>();

    public GovernmentSearchService(GovernmentSearchRepository repository) {
        this.repository = repository;
    }

    public List<GovernmentSearchResult> search(String query, Integer limit) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 2) {
            throw new IllegalArgumentException("Query must be at least 2 characters.");
        }

        int resolvedLimit = (limit == null || limit < 1) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return repository.searchWashingtonGovernments(normalizedQuery, resolvedLimit);
    }

    public List<GovernmentSearchResult> searchByZip(String zip, Integer limit) {
        String normalizedZip = zip == null ? "" : zip.trim();
        if (!ZIP_PATTERN.matcher(normalizedZip).matches()) {
            throw new IllegalArgumentException("zip must be a valid 5-digit ZIP code.");
        }

        int resolvedLimit = (limit == null || limit < 1) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return repository.findWashingtonGovernmentsByZip(normalizedZip, resolvedLimit);
    }

    public IncomeTaxStatusResponse getIncomeTaxStatus(String unitId, Integer year) {
        String normalizedUnitId = normalizeUnitId(unitId);
        int normalizedYear = normalizeYear(year);

        Set<String> incomeTaxUnits = incomeTaxUnitIdsByYear.computeIfAbsent(
                normalizedYear,
            yearKey -> repository.findWashingtonIncomeTaxUnitIdsForYear(yearKey)
        );

        boolean hasIncomeTax = incomeTaxUnits.contains(normalizedUnitId);
        return new IncomeTaxStatusResponse(normalizedUnitId, normalizedYear, hasIncomeTax);
    }

    private static String normalizeUnitId(String unitId) {
        String normalized = unitId == null ? "" : unitId.trim();
        if (normalized.length() != 12) {
            throw new IllegalArgumentException("unitId must be exactly 12 characters.");
        }
        return normalized;
    }

    private static int normalizeYear(Integer year) {
        int resolvedYear = year == null ? DEFAULT_YEAR : year;
        if (resolvedYear < 1900 || resolvedYear > 2100) {
            throw new IllegalArgumentException("year must be between 1900 and 2100.");
        }
        return resolvedYear;
    }
}
