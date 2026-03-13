package com.govlens.expense.api;

import java.util.List;

public record ExpenseCategorySlice(
        String category,
        Long amountThousands,
        Double percentage,
        Boolean aggregatedBucket,
        List<ExpenseCategoryItemSlice> items
) {
}
