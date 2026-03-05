package com.govlens.comparison.api;

public record ComparisonItem(
        String itemCode,
        String itemDescription,
        Long leftAmountThousands,
        Long rightAmountThousands,
        Long differenceThousands
) {
}
