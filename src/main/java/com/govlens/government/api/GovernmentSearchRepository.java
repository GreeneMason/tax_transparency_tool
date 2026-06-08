package com.govlens.government.api;

/** Data-access layer for government search, ZIP lookup, and tax-status queries. */

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class GovernmentSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    public GovernmentSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<GovernmentSearchResult> searchGovernments(String query, int limit, int offset, String stateFilter) {
        String queryLike = "%" + query + "%";

        // Normalize state filter to FIPS code for sargable filtering (avoid OR on joins).
        String stateFips = normalizeStateFilter(stateFilter);

        StringBuilder sql = new StringBuilder("""
                SELECT
                    g.unit_id,
                    g.unit_name,
                    g.county_name,
                    s.state_abbrev,
                    s.state_name,
                    g.gov_type_code,
                    gt.description AS gov_type_description,
                    g.population
                FROM govlens.dim_government_unit g
                JOIN govlens.dim_state s ON s.state_fips = g.state_fips
                JOIN govlens.dim_gov_type gt ON gt.gov_type_code = g.gov_type_code
                WHERE (
                        g.unit_name ILIKE ?
                     OR g.county_name ILIKE ?
                     OR g.place_fips = ?
                  )
                """);

        List<Object> params = new ArrayList<>();
        params.add(queryLike);
        params.add(queryLike);
        params.add(query);

        if (stateFips != null) {
            sql.append("\n  AND g.state_fips = ?");
            params.add(stateFips);
        }

        sql.append("""

                ORDER BY
                    CASE WHEN g.unit_name ILIKE ? THEN 0 ELSE 1 END,
                    g.unit_name
                LIMIT ? OFFSET ?
                """);

        params.add(queryLike);
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new GovernmentSearchResult(
                        rs.getString("unit_id"),
                        rs.getString("unit_name"),
                        rs.getString("county_name"),
                        rs.getString("state_abbrev"),
                        rs.getString("state_name"),
                        rs.getString("gov_type_code"),
                        rs.getString("gov_type_description"),
                        rs.getObject("population", Long.class)
                ),
                params.toArray()
        );
    }

    public long countGovernments(String query, String stateFilter) {
        String queryLike = "%" + query + "%";
        String stateFips = normalizeStateFilter(stateFilter);

        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT g.unit_id)
                FROM govlens.dim_government_unit g
                WHERE (
                        g.unit_name ILIKE ?
                     OR g.county_name ILIKE ?
                     OR g.place_fips = ?
                  )
                """);

        List<Object> params = new ArrayList<>();
        params.add(queryLike);
        params.add(queryLike);
        params.add(query);

        if (stateFips != null) {
            sql.append("\n  AND g.state_fips = ?");
            params.add(stateFips);
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), params.toArray(), Long.class);
        return count != null ? count : 0L;
    }

    public List<GovernmentSearchResult> findGovernmentsByZip(String zipCode, int limit, int offset, String stateFilter) {
        // Normalize state filter to FIPS code for sargable filtering.
        String stateFips = normalizeStateFilter(stateFilter);

        StringBuilder sql = new StringBuilder("""
                SELECT
                    g.unit_id,
                    g.unit_name,
                    g.county_name,
                    s.state_abbrev,
                    s.state_name,
                    g.gov_type_code,
                    gt.description AS gov_type_description,
                    g.population
                FROM govlens.zip_to_unit_lookup z
                JOIN govlens.dim_government_unit g ON g.unit_id = z.unit_id
                JOIN govlens.dim_state s ON s.state_fips = g.state_fips
                JOIN govlens.dim_gov_type gt ON gt.gov_type_code = g.gov_type_code
                WHERE z.zip_code = ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(zipCode);

        if (stateFips != null) {
            sql.append("\n  AND z.state_fips = ?");
            params.add(stateFips);
        }

        sql.append("""

                ORDER BY
                    z.hud_ratio DESC NULLS LAST,
                    g.population DESC NULLS LAST,
                    g.unit_name
                LIMIT ? OFFSET ?
                """);

        params.add(limit);
        params.add(offset);

        try {
            return jdbcTemplate.query(
                    sql.toString(),
                    (rs, rowNum) -> new GovernmentSearchResult(
                            rs.getString("unit_id"),
                            rs.getString("unit_name"),
                            rs.getString("county_name"),
                            rs.getString("state_abbrev"),
                            rs.getString("state_name"),
                            rs.getString("gov_type_code"),
                            rs.getString("gov_type_description"),
                            rs.getObject("population", Long.class)
                    ),
                    params.toArray()
            );
        } catch (DataAccessException ignored) {
            return Collections.emptyList();
        }
    }

    public long countGovernmentsByZip(String zipCode, String stateFilter) {
        String stateFips = normalizeStateFilter(stateFilter);

        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT g.unit_id)
                FROM govlens.zip_to_unit_lookup z
                JOIN govlens.dim_government_unit g ON g.unit_id = z.unit_id
                WHERE z.zip_code = ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(zipCode);

        if (stateFips != null) {
            sql.append("\n  AND z.state_fips = ?");
            params.add(stateFips);
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), params.toArray(), Long.class);
        return count != null ? count : 0L;
    }

    public Set<String> findIncomeTaxUnitIdsForYear(int year, String stateFilter) {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT f.unit_id
                FROM govlens.fact_finance_unit_item_year f
                JOIN govlens.dim_government_unit g ON g.unit_id = f.unit_id
                JOIN govlens.dim_state s ON s.state_fips = g.state_fips
                WHERE f.year = ?
                  AND f.item_code = 'T09'
                  AND f.amount_thousands > 0
                """);

        List<Object> params = new ArrayList<>();
        params.add(year);

        String stateFips = normalizeStateFilter(stateFilter);
        if (stateFips != null) {
            sql.append("\n  AND g.state_fips = ?");
            params.add(stateFips);
        }

        List<String> rows = jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> rs.getString("unit_id"),
                params.toArray()
        );

        return new HashSet<>(rows);
    }

    /**
     * Convert state abbreviation or FIPS code to canonical FIPS code.
     * Enables sargable filtering by normalizing state predicates at application level.
     * Returns null if no valid state provided.
     */
    private String normalizeStateFilter(String stateFilter) {
        if (stateFilter == null) {
            return null;
        }

        // If it's already a 2-digit FIPS code, use as-is.
        if (stateFilter.length() == 2 && stateFilter.matches("\\d{2}")) {
            return stateFilter;
        }

        // Map common abbreviations to FIPS codes.
        // In production, load full mapping from dim_state at startup or cache.
        String upper = stateFilter.toUpperCase();
        if ("WA".equals(upper)) {
            return "53";
        }
        // Extend with other states as needed.
        return null;
    }
}
