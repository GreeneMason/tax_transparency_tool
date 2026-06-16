package com.govlens.map;

/**
 * REST endpoints for the choropleth map page.
 *
 * GET /api/v1/map/item-codes
 *     Returns all distinct item codes with descriptions for the category dropdown.
 *
 * GET /api/v1/map/county-spending?itemCode=X&year=Y
 *     Returns county-level aggregated spending for one item code + year.
 *     itemCode = "__ALL__" aggregates across all categories.
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

    @GetMapping("/county-spending")
    public ResponseEntity<?> getCountySpending(
            @RequestParam("itemCode") String itemCode,
            @RequestParam("year") int year
    ) {
        if (year < 1900 || year > 2100) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid year."));
        }

        List<CountySpendingResult> results = "__ALL__".equals(itemCode)
                ? repository.findCountySpendingAll(year)
                : repository.findCountySpending(itemCode, year);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(results);
    }
}
