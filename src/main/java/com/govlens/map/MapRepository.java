package com.govlens.map;

/** Data-access layer for county-level spending aggregations used by the choropleth map. */

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MapRepository {

    private final JdbcTemplate jdbcTemplate;

    public MapRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns all distinct item codes with descriptions, ordered by description.
     * Used to populate the category dropdown on the map page.
     */
    public List<ItemCodeEntry> findAllItemCodes() {
        return jdbcTemplate.query(
            """
            SELECT item_code, description
            FROM govlens.dim_item_code
            ORDER BY description
            """,
            (rs, rowNum) -> new ItemCodeEntry(
                rs.getString("item_code"),
                rs.getString("description")
            )
        );
    }

    /**
     * Aggregates spending by county for a given item code and year.
     * Groups by county_fips + state_fips so multi-government counties are summed.
     */
    public List<CountySpendingResult> findCountySpending(String itemCode, int year) {
        return jdbcTemplate.query(
            """
            SELECT
                g.county_fips,
                g.state_fips,
                s.state_abbrev,
                g.county_name,
                SUM(f.amount_thousands)   AS total_amount_thousands,
                COALESCE(MAX(g.population), 0) AS population
            FROM govlens.fact_finance_unit_item_year f
            JOIN govlens.dim_government_unit g ON g.unit_id = f.unit_id
            JOIN govlens.dim_state           s ON s.state_fips = g.state_fips
            WHERE f.item_code = ?
              AND f.year      = ?
              AND g.county_fips IS NOT NULL
              AND g.county_fips <> ''
            GROUP BY g.county_fips, g.state_fips, s.state_abbrev, g.county_name
            ORDER BY g.state_fips, g.county_fips
            """,
            (rs, rowNum) -> new CountySpendingResult(
                rs.getString("county_fips"),
                rs.getString("state_fips"),
                rs.getString("state_abbrev"),
                rs.getString("county_name"),
                rs.getLong("total_amount_thousands"),
                rs.getLong("population")
            ),
            itemCode, year
        );
    }

    /**
     * Aggregates total spending across ALL item codes for a given year.
     */
    public List<CountySpendingResult> findCountySpendingAll(int year) {
        return jdbcTemplate.query(
            """
            SELECT
                g.county_fips,
                g.state_fips,
                s.state_abbrev,
                g.county_name,
                SUM(f.amount_thousands)        AS total_amount_thousands,
                COALESCE(MAX(g.population), 0) AS population
            FROM govlens.fact_finance_unit_item_year f
            JOIN govlens.dim_government_unit g ON g.unit_id = f.unit_id
            JOIN govlens.dim_state           s ON s.state_fips = g.state_fips
            WHERE f.year = ?
              AND g.county_fips IS NOT NULL
              AND g.county_fips <> ''
            GROUP BY g.county_fips, g.state_fips, s.state_abbrev, g.county_name
            ORDER BY g.state_fips, g.county_fips
            """,
            (rs, rowNum) -> new CountySpendingResult(
                rs.getString("county_fips"),
                rs.getString("state_fips"),
                rs.getString("state_abbrev"),
                rs.getString("county_name"),
                rs.getLong("total_amount_thousands"),
                rs.getLong("population")
            ),
            year
        );
    }

    /**
     * Returns all government type codes with descriptions, excluding state-level (code '0').
     * Used to populate the government layer dropdown on the map page.
     */
    public List<GovTypeEntry> findAllGovTypes() {
        return jdbcTemplate.query(
            """
            SELECT gov_type_code, description
            FROM govlens.dim_gov_type
            WHERE gov_type_code <> '0'
            ORDER BY gov_type_code
            """,
            (rs, rowNum) -> new GovTypeEntry(
                rs.getString("gov_type_code"),
                rs.getString("description")
            )
        );
    }

    /**
     * Aggregates spending by county for a given item code, year, and government type.
     */
    public List<CountySpendingResult> findCountySpendingByType(String itemCode, int year, String govTypeCode) {
        return jdbcTemplate.query(
            """
            SELECT
                g.county_fips,
                g.state_fips,
                s.state_abbrev,
                g.county_name,
                SUM(f.amount_thousands)        AS total_amount_thousands,
                COALESCE(MAX(g.population), 0) AS population
            FROM govlens.fact_finance_unit_item_year f
            JOIN govlens.dim_government_unit g ON g.unit_id = f.unit_id
            JOIN govlens.dim_state           s ON s.state_fips = g.state_fips
            WHERE f.item_code     = ?
              AND f.year          = ?
              AND g.gov_type_code = ?
              AND g.county_fips IS NOT NULL
              AND g.county_fips <> ''
            GROUP BY g.county_fips, g.state_fips, s.state_abbrev, g.county_name
            ORDER BY g.state_fips, g.county_fips
            """,
            (rs, rowNum) -> new CountySpendingResult(
                rs.getString("county_fips"),
                rs.getString("state_fips"),
                rs.getString("state_abbrev"),
                rs.getString("county_name"),
                rs.getLong("total_amount_thousands"),
                rs.getLong("population")
            ),
            itemCode, year, govTypeCode
        );
    }

    /**
     * Aggregates total spending across ALL item codes for a given year and government type.
     */
    public List<CountySpendingResult> findCountySpendingAllByType(int year, String govTypeCode) {
        return jdbcTemplate.query(
            """
            SELECT
                g.county_fips,
                g.state_fips,
                s.state_abbrev,
                g.county_name,
                SUM(f.amount_thousands)        AS total_amount_thousands,
                COALESCE(MAX(g.population), 0) AS population
            FROM govlens.fact_finance_unit_item_year f
            JOIN govlens.dim_government_unit g ON g.unit_id = f.unit_id
            JOIN govlens.dim_state           s ON s.state_fips = g.state_fips
            WHERE f.year          = ?
              AND g.gov_type_code = ?
              AND g.county_fips IS NOT NULL
              AND g.county_fips <> ''
            GROUP BY g.county_fips, g.state_fips, s.state_abbrev, g.county_name
            ORDER BY g.state_fips, g.county_fips
            """,
            (rs, rowNum) -> new CountySpendingResult(
                rs.getString("county_fips"),
                rs.getString("state_fips"),
                rs.getString("state_abbrev"),
                rs.getString("county_name"),
                rs.getLong("total_amount_thousands"),
                rs.getLong("population")
            ),
            year, govTypeCode
        );
    }
}
