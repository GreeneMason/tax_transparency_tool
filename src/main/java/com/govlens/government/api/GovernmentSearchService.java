package com.govlens.government.api;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GovernmentSearchService {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;

    private final GovernmentSearchRepository repository;

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
}
