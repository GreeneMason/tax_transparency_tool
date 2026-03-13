package com.govlens.government.api;

public record IncomeTaxStatusResponse(
        String unitId,
        Integer year,
        Boolean hasIncomeTax
) {
}
