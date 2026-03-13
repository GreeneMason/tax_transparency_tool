package com.govlens.expense.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/governments")
public class ExpenseBreakdownController {

    private final ExpenseBreakdownService service;

    public ExpenseBreakdownController(ExpenseBreakdownService service) {
        this.service = service;
    }

    @GetMapping("/{unitId}/expense-breakdown")
    public ResponseEntity<?> getExpenseBreakdown(
            @PathVariable("unitId") String unitId,
            @RequestParam("year") Integer year
    ) {
        try {
            ExpenseBreakdownResponse response = service.getBreakdown(unitId, year);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
