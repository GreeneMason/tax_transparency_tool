package com.govlens.government.api;

/** Service-layer validation and orchestration for government search operations. */

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
    private static final Pattern STATE_PATTERN = Pattern.compile("^(?:\\d{2}|[A-Za-z]{2})$");

    private final GovernmentSearchRepository repository;
    private final Map<String, Set<String>> incomeTaxUnitIdsByYearAndState = new ConcurrentHashMap<>();

    public GovernmentSearchService(GovernmentSearchRepository repository) {
        this.repository = repository;
    }

    public List<GovernmentSearchResult> search(String query, int limit, int offset, String state) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 2) {
            throw new IllegalArgumentException("Query must be at least 2 characters.");
        }

        String normalizedState = normalizeStateFilter(state);
        int resolvedOffset = Math.max(offset, 0);
        int resolvedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return repository.searchGovernments(normalizedQuery, resolvedLimit, resolvedOffset, normalizedState);
    }

    public long countGovernments(String query, String state) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 2) {
            throw new IllegalArgumentException("Query must be at least 2 characters.");
        }

        String normalizedState = normalizeStateFilter(state);
        return repository.countGovernments(normalizedQuery, normalizedState);
    }

    public List<GovernmentSearchResult> searchByZip(String zip, int limit, int offset, String state) {
        String normalizedZip = zip == null ? "" : zip.trim();
        if (!ZIP_PATTERN.matcher(normalizedZip).matches()) {
            throw new IllegalArgumentException("zip must be a valid 5-digit ZIP code.");
        }

        String normalizedState = normalizeStateFilter(state);
        int resolvedOffset = Math.max(offset, 0);
        int resolvedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return repository.findGovernmentsByZip(normalizedZip, resolvedLimit, resolvedOffset, normalizedState);
    }

    public long countGovernmentsByZip(String zip, String state) {
        String normalizedZip = zip == null ? "" : zip.trim();
        if (!ZIP_PATTERN.matcher(normalizedZip).matches()) {
            throw new IllegalArgumentException("zip must be a valid 5-digit ZIP code.");
        }

        String normalizedState = normalizeStateFilter(state);
        return repository.countGovernmentsByZip(normalizedZip, normalizedState);
    }

    public IncomeTaxStatusResponse getIncomeTaxStatus(String unitId, Integer year, String state) {
        String normalizedUnitId = normalizeUnitId(unitId);
        int normalizedYear = normalizeYear(year);
        String normalizedState = normalizeStateFilter(state);
        String cacheKey = normalizedYear + "|" + (normalizedState == null ? "ALL" : normalizedState);

        Set<String> incomeTaxUnits = incomeTaxUnitIdsByYearAndState.computeIfAbsent(
                cacheKey,
                key -> repository.findIncomeTaxUnitIdsForYear(normalizedYear, normalizedState)
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

    private static String normalizeStateFilter(String state) {
        String normalized = state == null ? "" : state.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!STATE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("state must be a 2-digit FIPS or 2-letter abbreviation.");
        }
        return normalized.toUpperCase();
    }
}
