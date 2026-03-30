package com.govlens.government.api;

/** REST endpoints for searching governments and checking income-tax status. */

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
     * Searches government units by free-text query.
     *
     * @param query search text (minimum length validation handled in service)
     * @param limit optional max number of results
     * @param state optional state filter (2-letter abbreviation or 2-digit FIPS)
     * @return matching government rows, or a 400 error payload when input is invalid
     */
    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam("query") String query,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "state", required = false) String state
    ) {
        try {
            List<GovernmentSearchResult> results = service.search(query, limit, state);
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Looks up government units by ZIP code.
     *
     * @param zip ZIP code to resolve
     * @param limit optional max number of results
     * @param state optional state filter (2-letter abbreviation or 2-digit FIPS)
     * @return matching government rows, or a 400 error payload when input is invalid
     */
    @GetMapping("/by-zip")
    public ResponseEntity<?> byZip(
            @RequestParam("zip") String zip,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "state", required = false) String state
    ) {
        try {
            List<GovernmentSearchResult> results = service.searchByZip(zip, limit, state);
            return ResponseEntity.ok(results);
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
