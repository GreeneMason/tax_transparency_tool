package com.govlens.comparison.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ComparisonRepository {

    private final JdbcTemplate jdbcTemplate;

    public ComparisonRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<GovernmentSummary> findWashingtonGovernment(String unitId) {
        String sql = """
                SELECT
                    g.unit_id,
                    g.unit_name,
                    g.county_name,
                    gt.description AS gov_type_description,
                    g.population
                FROM govlens.dim_government_unit g
                JOIN govlens.dim_gov_type gt ON gt.gov_type_code = g.gov_type_code
                WHERE g.unit_id = ?
                  AND g.state_fips = '53'
                """;

        List<GovernmentSummary> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new GovernmentSummary(
                        rs.getString("unit_id"),
                        rs.getString("unit_name"),
                        rs.getString("county_name"),
                        rs.getString("gov_type_description"),
                        rs.getObject("population", Long.class)
                ),
                unitId
        );

        return rows.stream().findFirst();
    }

    public List<ComparisonItem> compareByItemCode(String leftUnitId, String rightUnitId, int year) {
        String sql = """
                WITH left_data AS (
                    SELECT item_code, amount_thousands
                    FROM govlens.fact_finance_unit_item_year
                    WHERE unit_id = ? AND year = ?
                ),
                right_data AS (
                    SELECT item_code, amount_thousands
                    FROM govlens.fact_finance_unit_item_year
                    WHERE unit_id = ? AND year = ?
                )
                SELECT
                    COALESCE(l.item_code, r.item_code) AS item_code,
                    ic.description AS item_description,
                    l.amount_thousands AS left_amount,
                    r.amount_thousands AS right_amount,
                    COALESCE(l.amount_thousands, 0) - COALESCE(r.amount_thousands, 0) AS difference_amount
                FROM left_data l
                FULL OUTER JOIN right_data r ON r.item_code = l.item_code
                JOIN govlens.dim_item_code ic ON ic.item_code = COALESCE(l.item_code, r.item_code)
                ORDER BY ABS(COALESCE(l.amount_thousands, 0) - COALESCE(r.amount_thousands, 0)) DESC,
                         COALESCE(l.item_code, r.item_code)
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ComparisonItem(
                        rs.getString("item_code"),
                        rs.getString("item_description"),
                        rs.getObject("left_amount", Long.class),
                        rs.getObject("right_amount", Long.class),
                        rs.getObject("difference_amount", Long.class)
                ),
                leftUnitId,
                year,
                rightUnitId,
                year
        );
    }
}
