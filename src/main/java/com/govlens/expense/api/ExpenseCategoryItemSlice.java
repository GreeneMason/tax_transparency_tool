package com.govlens.expense.api;

public record ExpenseCategoryItemSlice(
        String itemCode,
        String itemDescription,
        Long amountThousands,
        Double percentageWithinCategory
) {
}
