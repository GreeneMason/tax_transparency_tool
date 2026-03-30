package com.govlens.comparison.api;

/** Item-level comparison row for two selected governments. */

public record ComparisonItem(
        String itemCode,
        String itemDescription,
        Long leftAmountThousands,
        Long rightAmountThousands,
        Long differenceThousands
) {
}
