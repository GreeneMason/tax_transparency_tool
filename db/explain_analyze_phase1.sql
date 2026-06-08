-- Phase 1 query-plan validation for production index readiness.
-- Usage:
--   psql -d <database_name> -f db/explain_analyze_phase1.sql
--
-- This script runs EXPLAIN ANALYZE against key API query patterns.
-- It auto-selects representative unit IDs from existing data so it can run
-- without manual edits after a normal data load.

\echo '--- 1) Government search (ILIKE unit/county) ---'
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
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
        g.unit_name ILIKE '%sea%'
     OR g.county_name ILIKE '%sea%'
     OR g.place_fips = '00000'
  )
  AND (g.state_fips = '53' OR s.state_abbrev = 'WA')
ORDER BY
    CASE WHEN g.unit_name ILIKE '%sea%' THEN 0 ELSE 1 END,
    g.unit_name
LIMIT 25;

\echo '--- 2) ZIP lookup with state filter and ranking ---'
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
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
WHERE z.zip_code = '98101'
  AND (g.state_fips = '53' OR s.state_abbrev = 'WA')
ORDER BY
    z.hud_ratio DESC NULLS LAST,
    g.population DESC NULLS LAST,
    g.unit_name
LIMIT 25;

\echo '--- 3) Income tax status lookup (year + item + amount) ---'
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT DISTINCT f.unit_id
FROM govlens.fact_finance_unit_item_year f
JOIN govlens.dim_government_unit g ON g.unit_id = f.unit_id
JOIN govlens.dim_state s ON s.state_fips = g.state_fips
WHERE f.year = 2023
  AND f.item_code = 'T09'
  AND f.amount_thousands > 0
  AND (g.state_fips = '53' OR s.state_abbrev = 'WA');

\echo '--- 4) Comparison endpoint (unit/year item-level FULL JOIN) ---'
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
WITH selected_units AS (
    SELECT unit_id, ROW_NUMBER() OVER (ORDER BY population DESC NULLS LAST, unit_name) AS rn
    FROM govlens.dim_government_unit
    WHERE state_fips = '53'
),
left_data AS (
    SELECT f.item_code, f.amount_thousands
    FROM govlens.fact_finance_unit_item_year f
    JOIN selected_units u ON u.unit_id = f.unit_id
    WHERE u.rn = 1
      AND f.year = 2023
),
right_data AS (
    SELECT f.item_code, f.amount_thousands
    FROM govlens.fact_finance_unit_item_year f
    JOIN selected_units u ON u.unit_id = f.unit_id
    WHERE u.rn = 2
      AND f.year = 2023
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
         COALESCE(l.item_code, r.item_code);

\echo '--- 5) Expense breakdown endpoint (per-unit/year category aggregate) ---'
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
WITH selected_unit AS (
    SELECT unit_id
    FROM govlens.dim_government_unit
    WHERE state_fips = '53'
    ORDER BY population DESC NULLS LAST, unit_name
    LIMIT 1
)
SELECT
    CASE
        WHEN f.item_code LIKE 'E__' THEN 'Current Operations'
        WHEN f.item_code LIKE 'F__' THEN 'Construction'
        WHEN f.item_code LIKE 'I__' THEN 'Interest on Debt'
        WHEN f.item_code LIKE 'J__' THEN 'Assistance & Subsidies'
        WHEN f.item_code LIKE 'L__' OR f.item_code LIKE 'M__' OR f.item_code LIKE 'Q__' THEN 'Intergovernmental Expenditures'
        ELSE 'Other Expenses'
    END AS category,
    SUM(f.amount_thousands)::BIGINT AS amount_thousands
FROM govlens.fact_finance_unit_item_year f
JOIN selected_unit u ON u.unit_id = f.unit_id
WHERE f.year = 2023
  AND f.amount_thousands > 0
  AND (
      f.item_code LIKE 'E__'
      OR f.item_code LIKE 'F__'
      OR f.item_code LIKE 'I__'
      OR f.item_code LIKE 'J__'
      OR f.item_code LIKE 'L__'
      OR f.item_code LIKE 'M__'
      OR f.item_code LIKE 'Q__'
  )
GROUP BY category
ORDER BY amount_thousands DESC;
