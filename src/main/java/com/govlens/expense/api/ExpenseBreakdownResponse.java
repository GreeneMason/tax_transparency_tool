package com.govlens.expense.api;

import java.util.List;

public record ExpenseBreakdownResponse(
        Integer year,
        ExpenseGovernmentSummary government,
        Long totalExpensesThousands,
        List<ExpenseCategorySlice> categories
) {
}
