package com.govlens.expense.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ExpenseBreakdownRepository {

    private static final String CATEGORY_CASE = """
            CASE
                WHEN f.item_code LIKE 'E__' THEN 'Current Operations'
                WHEN f.item_code LIKE 'F__' THEN 'Construction'
                WHEN f.item_code LIKE 'I__' THEN 'Interest on Debt'
                WHEN f.item_code LIKE 'J__' THEN 'Assistance & Subsidies'
                WHEN f.item_code LIKE 'L__' OR f.item_code LIKE 'M__' OR f.item_code LIKE 'Q__' THEN 'Intergovernmental Expenditures'
                ELSE 'Other Expenses'
            END
            """;

    private static final String INCLUDED_ITEM_CODES_FILTER = """
            (
                f.item_code LIKE 'E__'
                OR f.item_code LIKE 'F__'
                OR f.item_code LIKE 'I__'
                OR f.item_code LIKE 'J__'
                OR f.item_code LIKE 'L__'
                OR f.item_code LIKE 'M__'
                OR f.item_code LIKE 'Q__'
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public ExpenseBreakdownRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ExpenseGovernmentSummary> findWashingtonGovernment(String unitId) {
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

        List<ExpenseGovernmentSummary> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ExpenseGovernmentSummary(
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

    public List<CategoryTotal> findExpenseCategoryTotals(String unitId, int year) {
                String sql = """
                SELECT
                                        %s AS category,
                    SUM(f.amount_thousands)::BIGINT AS amount_thousands
                FROM govlens.fact_finance_unit_item_year f
                WHERE f.unit_id = ?
                  AND f.year = ?
                  AND f.amount_thousands > 0
                                    AND %s
                GROUP BY category
                ORDER BY amount_thousands DESC
                                """.formatted(CATEGORY_CASE, INCLUDED_ITEM_CODES_FILTER);

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new CategoryTotal(
                        rs.getString("category"),
                        rs.getLong("amount_thousands")
                ),
                unitId,
                year
        );
    }

            public List<CategoryItemTotal> findExpenseCategoryItemTotals(String unitId, int year) {
            String sql = """
                SELECT
                    %s AS category,
                    f.item_code,
                    COALESCE(ic.description, f.item_code) AS item_description,
                    SUM(f.amount_thousands)::BIGINT AS amount_thousands
                FROM govlens.fact_finance_unit_item_year f
                LEFT JOIN govlens.dim_item_code ic ON ic.item_code = f.item_code
                WHERE f.unit_id = ?
                  AND f.year = ?
                  AND f.amount_thousands > 0
                  AND %s
                GROUP BY category, f.item_code, item_description
                ORDER BY category, amount_thousands DESC, f.item_code
                """.formatted(CATEGORY_CASE, INCLUDED_ITEM_CODES_FILTER);

            return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new CategoryItemTotal(
                    rs.getString("category"),
                    rs.getString("item_code"),
                    rs.getString("item_description"),
                    rs.getLong("amount_thousands")
                ),
                unitId,
                year
            );
            }

    public record CategoryTotal(String category, Long amountThousands) {
    }

            public record CategoryItemTotal(
                String category,
                String itemCode,
                String itemDescription,
                Long amountThousands
            ) {
            }
}
