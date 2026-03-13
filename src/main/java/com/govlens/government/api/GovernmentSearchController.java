package com.govlens.government.api;

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
public class GovernmentSearchController {

    private final GovernmentSearchService service;

    public GovernmentSearchController(GovernmentSearchService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam("query") String query,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        try {
            List<GovernmentSearchResult> results = service.search(query, limit);
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/by-zip")
    public ResponseEntity<?> byZip(
            @RequestParam("zip") String zip,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        try {
            List<GovernmentSearchResult> results = service.searchByZip(zip, limit);
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/{unitId}/income-tax-status")
    public ResponseEntity<?> incomeTaxStatus(
            @PathVariable("unitId") String unitId,
            @RequestParam(value = "year", required = false) Integer year
    ) {
        try {
            IncomeTaxStatusResponse response = service.getIncomeTaxStatus(unitId, year);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
