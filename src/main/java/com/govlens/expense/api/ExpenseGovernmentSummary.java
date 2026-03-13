package com.govlens.expense.api;

public record ExpenseGovernmentSummary(
        String unitId,
        String unitName,
        String countyName,
        String govTypeDescription,
        Long population
) {
}
