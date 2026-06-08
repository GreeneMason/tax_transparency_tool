package com.govlens.government.api;

/** REST endpoints for searching governments and checking income-tax status. */

import com.govlens.common.PaginatedResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/governments")
/**
 * REST endpoints for government search, ZIP-based lookup, and local income-tax status checks.
 */
public class GovernmentSearchController {

    private final GovernmentSearchService service;

    public GovernmentSearchController(GovernmentSearchService service) {
        this.service = service;
    }

    /**
     * Searches government units by free-text query with pagination support.
     *
     * @param query search text (minimum length validation handled in service)
     * @param limit optional max number of results (default 25, max 100)
     * @param offset optional pagination offset (default 0)
     * @param state optional state filter (2-letter abbreviation or 2-digit FIPS)
     * @return paginated government rows, or a 400 error payload when input is invalid
     */
    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam("query") String query,
            @RequestParam(value = "limit", defaultValue = "25") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "state", required = false) String state
    ) {
        try {
            // Enforce reasonable limits
            limit = Math.min(Math.max(limit, 1), 100);
            offset = Math.max(offset, 0);

            List<GovernmentSearchResult> results = service.search(query, limit + 1, offset, state);
            
            // Check if there are more results
            boolean hasMore = results.size() > limit;
            if (hasMore) {
                results = results.subList(0, limit);
            }

            long totalCount = service.countGovernments(query, state);
            PaginatedResponse<GovernmentSearchResult> response = new PaginatedResponse<>(
                    results, limit, offset, totalCount
            );

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Looks up government units by ZIP code with pagination support.
     *
     * @param zip ZIP code to resolve
     * @param limit optional max number of results (default 25, max 100)
     * @param offset optional pagination offset (default 0)
     * @param state optional state filter (2-letter abbreviation or 2-digit FIPS)
     * @return paginated government rows, or a 400 error payload when input is invalid
     */
    @GetMapping("/by-zip")
    public ResponseEntity<?> byZip(
            @RequestParam("zip") String zip,
            @RequestParam(value = "limit", defaultValue = "25") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "state", required = false) String state
    ) {
        try {
            limit = Math.min(Math.max(limit, 1), 100);
            offset = Math.max(offset, 0);

            List<GovernmentSearchResult> results = service.searchByZip(zip, limit + 1, offset, state);

            boolean hasMore = results.size() > limit;
            if (hasMore) {
                results = results.subList(0, limit);
            }

            long totalCount = service.countGovernmentsByZip(zip, state);
            PaginatedResponse<GovernmentSearchResult> response = new PaginatedResponse<>(
                    results, limit, offset, totalCount
            );

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Returns whether the selected government has local income tax activity for a year.
     *
     * @param unitId 12-character government unit id
     * @param year optional tax year (defaults handled in service)
     * @param state optional state filter (2-letter abbreviation or 2-digit FIPS)
     * @return income-tax status response, or a 400 error payload when input is invalid
     */
    @GetMapping("/{unitId}/income-tax-status")
    public ResponseEntity<?> incomeTaxStatus(
            @PathVariable("unitId") String unitId,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "state", required = false) String state
    ) {
        try {
            IncomeTaxStatusResponse response = service.getIncomeTaxStatus(unitId, year, state);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
