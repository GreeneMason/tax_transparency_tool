package com.govlens.expense.api;

/** Category-level expense slice with optional item-level details. */

import java.util.List;

public record ExpenseCategorySlice(
        String category,
        Long amountThousands,
        Double percentage,
        Boolean aggregatedBucket,
        List<ExpenseCategoryItemSlice> items
) {
}
