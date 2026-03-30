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

    public List<GovernmentSearchResult> searchGovernments(String query, int limit, String stateFilter) {
        String queryLike = "%" + query + "%";

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

        if (stateFilter != null) {
            sql.append("\n  AND (g.state_fips = ? OR s.state_abbrev = ?)");
            params.add(stateFilter);
            params.add(stateFilter);
        }

        sql.append("""

                ORDER BY
                    CASE WHEN g.unit_name ILIKE ? THEN 0 ELSE 1 END,
                    g.unit_name
                LIMIT ?
                """);

        params.add(queryLike);
        params.add(limit);

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

    public List<GovernmentSearchResult> findGovernmentsByZip(String zipCode, int limit, String stateFilter) {
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

        if (stateFilter != null) {
            sql.append("\n  AND (g.state_fips = ? OR s.state_abbrev = ?)");
            params.add(stateFilter);
            params.add(stateFilter);
        }

        sql.append("""

                ORDER BY
                    z.hud_ratio DESC NULLS LAST,
                    g.population DESC NULLS LAST,
                    g.unit_name
                LIMIT ?
                """);

        params.add(limit);

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

        if (stateFilter != null) {
            sql.append("\n  AND (g.state_fips = ? OR s.state_abbrev = ?)");
            params.add(stateFilter);
            params.add(stateFilter);
        }

        List<String> rows = jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> rs.getString("unit_id"),
                params.toArray()
        );

        return new HashSet<>(rows);
    }
}
