package com.govlens.map;

/** Response DTO for a single county's aggregated spending value for the choropleth map. */
public class CountySpendingResult {

    private final String countyFips;
    private final String stateFips;
    private final String stateAbbrev;
    private final String countyName;
    private final long   totalAmountThousands;
    private final long   population;

    public CountySpendingResult(
            String countyFips,
            String stateFips,
            String stateAbbrev,
            String countyName,
            long   totalAmountThousands,
            long   population
    ) {
        this.countyFips           = countyFips;
        this.stateFips            = stateFips;
        this.stateAbbrev          = stateAbbrev;
        this.countyName           = countyName;
        this.totalAmountThousands = totalAmountThousands;
        this.population           = population;
    }

    public String getCountyFips()           { return countyFips; }
    public String getStateFips()            { return stateFips; }
    public String getStateAbbrev()          { return stateAbbrev; }
    public String getCountyName()           { return countyName; }
    public long   getTotalAmountThousands() { return totalAmountThousands; }
    public long   getPopulation()           { return population; }

    /** Derived: per-capita dollars (amount is stored in thousands). */
    public double getPerCapita() {
        if (population <= 0) return 0.0;
        return (totalAmountThousands * 1000.0) / population;
    }
}
