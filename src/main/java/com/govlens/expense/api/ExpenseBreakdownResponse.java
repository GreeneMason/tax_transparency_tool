package com.govlens.expense.api;

/** API response payload for government expense breakdown results. */

import java.util.List;

public record ExpenseBreakdownResponse(
        Integer year,
        ExpenseGovernmentSummary government,
        Long totalExpensesThousands,
        List<ExpenseCategorySlice> categories
) {
}
