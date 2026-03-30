package com.govlens.expense.api;

/** Lightweight government summary used by expense responses. */

public record ExpenseGovernmentSummary(
        String unitId,
        String unitName,
        String countyName,
        String govTypeDescription,
        Long population
) {
}
