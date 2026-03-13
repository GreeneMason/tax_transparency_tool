package com.govlens.expense.api;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class ExpenseBreakdownService {

    private final ExpenseBreakdownRepository repository;

    public ExpenseBreakdownService(ExpenseBreakdownRepository repository) {
        this.repository = repository;
    }

    public ExpenseBreakdownResponse getBreakdown(String unitId, Integer year) {
        String normalizedUnitId = normalizeUnitId(unitId);
        int normalizedYear = normalizeYear(year);

        ExpenseGovernmentSummary government = repository.findWashingtonGovernment(normalizedUnitId)
                .orElseThrow(() -> new NoSuchElementException("Government not found in Washington dataset."));

        List<ExpenseBreakdownRepository.CategoryTotal> totals = repository.findExpenseCategoryTotals(normalizedUnitId, normalizedYear);
        List<ExpenseBreakdownRepository.CategoryItemTotal> itemTotals = repository.findExpenseCategoryItemTotals(normalizedUnitId, normalizedYear);

        Map<String, List<ExpenseBreakdownRepository.CategoryItemTotal>> itemsByCategory = new LinkedHashMap<>();
        itemTotals.forEach(item -> itemsByCategory
            .computeIfAbsent(item.category(), key -> new java.util.ArrayList<>())
            .add(item)
        );

        long totalExpenses = totals.stream()
                .mapToLong(value -> value.amountThousands() == null ? 0L : value.amountThousands())
                .sum();

        List<ExpenseCategorySlice> categories = totals.stream()
            .map(value -> {
                List<ExpenseBreakdownRepository.CategoryItemTotal> categoryItems = itemsByCategory.getOrDefault(value.category(), Collections.emptyList());
                List<ExpenseCategoryItemSlice> itemSlices = toItemSlices(categoryItems, value.amountThousands());

                return new ExpenseCategorySlice(
                    value.category(),
                    value.amountThousands(),
                    percentage(value.amountThousands(), totalExpenses),
                    !itemSlices.isEmpty(),
                    itemSlices
                );
            })
                .toList();

        return new ExpenseBreakdownResponse(normalizedYear, government, totalExpenses, categories);
    }

    private static String normalizeUnitId(String unitId) {
        String normalized = unitId == null ? "" : unitId.trim();
        if (normalized.length() != 12) {
            throw new IllegalArgumentException("unitId must be exactly 12 characters.");
        }
        return normalized;
    }

    private static int normalizeYear(Integer year) {
        if (year == null) {
            throw new IllegalArgumentException("year is required.");
        }
        if (year < 1900 || year > 2100) {
            throw new IllegalArgumentException("year must be between 1900 and 2100.");
        }
        return year;
    }

    private static double percentage(Long amount, long total) {
        if (amount == null || total <= 0) {
            return 0.0;
        }
        double raw = (amount * 100.0) / total;
        return Math.round(raw * 100.0) / 100.0;
    }

    private static List<ExpenseCategoryItemSlice> toItemSlices(
            List<ExpenseBreakdownRepository.CategoryItemTotal> categoryItems,
            Long categoryTotal
    ) {
        long normalizedCategoryTotal = categoryTotal == null ? 0L : categoryTotal;

        return categoryItems.stream()
                .map(item -> new ExpenseCategoryItemSlice(
                        item.itemCode(),
                        item.itemDescription(),
                        item.amountThousands(),
                        percentage(item.amountThousands(), normalizedCategoryTotal)
                ))
                .toList();
    }
}
