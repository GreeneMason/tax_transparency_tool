package com.govlens.government.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
