package com.govlens.map;

/**
 * REST endpoints for the choropleth map page.
 *
 * GET /api/v1/map/item-codes
 *     Returns all distinct item codes with descriptions for the category dropdown.
 *
 * GET /api/v1/map/gov-types
 *     Returns all government type codes for the layer dropdown.
 *
 * GET /api/v1/map/county-spending?itemCode=X&year=Y[&govTypeCode=Z]
 *     Returns county-level aggregated spending.
 *     itemCode = "__ALL__" aggregates across all categories.
 *     govTypeCode omitted or "__ALL__" aggregates across all government types.
 *
 * Responses are small enough to be cached by the browser (Cache-Control: max-age).
 */

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/map")
public class MapController {

    private final MapRepository repository;

    public MapController(MapRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/item-codes")
    public ResponseEntity<?> getItemCodes() {
        List<ItemCodeEntry> codes = repository.findAllItemCodes();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS))
                .body(codes);
    }

    @GetMapping("/gov-types")
    public ResponseEntity<?> getGovTypes() {
        List<GovTypeEntry> types = repository.findAllGovTypes();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS))
                .body(types);
    }

    @GetMapping("/county-spending")
    public ResponseEntity<?> getCountySpending(
            @RequestParam("itemCode") String itemCode,
            @RequestParam("year") int year,
            @RequestParam(value = "govTypeCode", required = false) String govTypeCode
    ) {
        if (year < 1900 || year > 2100) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid year."));
        }

        String normalizedGovTypeCode = govTypeCode == null ? null : govTypeCode.trim();
        boolean allTypes = normalizedGovTypeCode == null
                || normalizedGovTypeCode.isBlank()
                || "__ALL__".equals(normalizedGovTypeCode);
        boolean allItems = "__ALL__".equals(itemCode);

        List<CountySpendingResult> results;
        if (allItems && allTypes) {
            results = repository.findCountySpendingAll(year);
        } else if (allItems) {
            results = repository.findCountySpendingAllByType(year, normalizedGovTypeCode);
        } else if (allTypes) {
            results = repository.findCountySpending(itemCode, year);
        } else {
            results = repository.findCountySpendingByType(itemCode, year, normalizedGovTypeCode);
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(results);
    }
}
