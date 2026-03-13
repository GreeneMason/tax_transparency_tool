package com.govlens.government.api;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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

    public List<GovernmentSearchResult> searchWashingtonGovernments(String query, int limit) {
        String queryLike = "%" + query + "%";

        String sql = """
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
                WHERE g.state_fips = '53'
                  AND (
                        g.unit_name ILIKE ?
                     OR g.county_name ILIKE ?
                     OR g.place_fips = ?
                  )
                ORDER BY
                    CASE WHEN g.unit_name ILIKE ? THEN 0 ELSE 1 END,
                    g.unit_name
                LIMIT ?
                """;

        return jdbcTemplate.query(
                sql,
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
                queryLike,
                queryLike,
                query,
                queryLike,
                limit
        );
    }

    public List<GovernmentSearchResult> findWashingtonGovernmentsByZip(String zipCode, int limit) {
        String sql = """
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
                  AND g.state_fips = '53'
                ORDER BY
                    z.hud_ratio DESC NULLS LAST,
                    g.population DESC NULLS LAST,
                    g.unit_name
                LIMIT ?
                """;

        try {
            return jdbcTemplate.query(
                    sql,
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
                    zipCode,
                    limit
            );
        } catch (DataAccessException ignored) {
            return Collections.emptyList();
        }
    }

    public Set<String> findWashingtonIncomeTaxUnitIdsForYear(int year) {
        String sql = """
                SELECT DISTINCT f.unit_id
                FROM govlens.fact_finance_unit_item_year f
                JOIN govlens.dim_government_unit g ON g.unit_id = f.unit_id
                WHERE g.state_fips = '53'
                  AND f.year = ?
                  AND f.item_code = 'T09'
                  AND f.amount_thousands > 0
                """;

        List<String> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getString("unit_id"),
                year
        );

        return new HashSet<>(rows);
    }
}
