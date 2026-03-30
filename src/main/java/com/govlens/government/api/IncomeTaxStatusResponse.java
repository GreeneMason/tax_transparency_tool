package com.govlens.government.api;

/** Response payload indicating whether local income-tax activity is present. */

public record IncomeTaxStatusResponse(
        String unitId,
        Integer year,
        Boolean hasIncomeTax
) {
}
