package com.govlens.map;

/** Response DTO for a government type entry used to populate the map layer dropdown. */
public class GovTypeEntry {

    private final String govTypeCode;
    private final String govTypeDescription;

    public GovTypeEntry(String govTypeCode, String govTypeDescription) {
        this.govTypeCode        = govTypeCode;
        this.govTypeDescription = govTypeDescription;
    }

    public String getGovTypeCode()        { return govTypeCode; }
    public String getGovTypeDescription() { return govTypeDescription; }
}
