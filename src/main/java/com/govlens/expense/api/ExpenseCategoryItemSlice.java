package com.govlens.expense.api;

/** Item-level slice inside a broader expense category. */

public record ExpenseCategoryItemSlice(
        String itemCode,
        String itemDescription,
        Long amountThousands,
        Double percentageWithinCategory
) {
}
