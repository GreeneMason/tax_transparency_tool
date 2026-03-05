package com.govlens.comparison.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/compare")
public class ComparisonController {

    private final ComparisonService service;

    public ComparisonController(ComparisonService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> compare(
            @RequestParam("leftUnitId") String leftUnitId,
            @RequestParam("rightUnitId") String rightUnitId,
            @RequestParam("year") Integer year
    ) {
        try {
            ComparisonResponse response = service.compare(leftUnitId, rightUnitId, year);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
