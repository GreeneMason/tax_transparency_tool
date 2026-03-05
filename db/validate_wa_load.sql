-- GovLens Washington load validation checks

-- 1) Basic row-count sanity
SELECT 'staging_rows' AS check_name, COUNT(*)::BIGINT AS check_value
FROM govlens.stg_finance_unit_item
UNION ALL
SELECT 'fact_rows', COUNT(*)::BIGINT
FROM govlens.fact_finance_unit_item_year
UNION ALL
SELECT 'dim_government_units', COUNT(*)::BIGINT
FROM govlens.dim_government_unit
UNION ALL
SELECT 'dim_item_codes', COUNT(*)::BIGINT
FROM govlens.dim_item_code
UNION ALL
SELECT 'dim_imputation_flags', COUNT(*)::BIGINT
FROM govlens.dim_imputation_flag
UNION ALL
SELECT 'view_rows', COUNT(*)::BIGINT
FROM govlens.vw_finance_enriched;

-- 2) Duplicate natural keys in fact (should be 0)
SELECT COUNT(*)::BIGINT AS duplicate_fact_keys
FROM (
    SELECT unit_id, year, item_code, COUNT(*) AS c
    FROM govlens.fact_finance_unit_item_year
    GROUP BY unit_id, year, item_code
    HAVING COUNT(*) > 1
) d;

-- 3) Referential integrity spot checks (should be 0)
SELECT COUNT(*)::BIGINT AS facts_missing_unit
FROM govlens.fact_finance_unit_item_year f
LEFT JOIN govlens.dim_government_unit g ON g.unit_id = f.unit_id
WHERE g.unit_id IS NULL;

SELECT COUNT(*)::BIGINT AS facts_missing_item
FROM govlens.fact_finance_unit_item_year f
LEFT JOIN govlens.dim_item_code i ON i.item_code = f.item_code
WHERE i.item_code IS NULL;

SELECT COUNT(*)::BIGINT AS facts_missing_flag
FROM govlens.fact_finance_unit_item_year f
LEFT JOIN govlens.dim_imputation_flag fl ON fl.flag = f.imputation_flag
WHERE fl.flag IS NULL;

-- 4) Washington-only MVP scope check (should be 1 state, '53')
SELECT state_fips, COUNT(*)::BIGINT AS fact_rows
FROM govlens.vw_finance_enriched
GROUP BY state_fips
ORDER BY state_fips;

-- 5) Unknown descriptions after enrichment (should be 0)
SELECT COUNT(*)::BIGINT AS unknown_item_descriptions
FROM govlens.vw_finance_enriched
WHERE item_description IS NULL OR item_description = '';

SELECT COUNT(*)::BIGINT AS unknown_flag_descriptions
FROM govlens.vw_finance_enriched
WHERE imputation_flag_description IS NULL OR imputation_flag_description = '';

SELECT COUNT(*)::BIGINT AS unknown_gov_type_descriptions
FROM govlens.vw_finance_enriched
WHERE gov_type_description IS NULL OR gov_type_description = '';

-- 6) Data completeness diagnostics (informational)
SELECT
    COUNT(*)::BIGINT AS total_units,
    COUNT(*) FILTER (WHERE unit_name IS NULL OR unit_name = '')::BIGINT AS units_missing_name,
    COUNT(*) FILTER (WHERE population IS NULL)::BIGINT AS units_missing_population
FROM govlens.dim_government_unit;

SELECT
    imputation_flag,
    imputation_flag_description,
    COUNT(*)::BIGINT AS row_count
FROM govlens.vw_finance_enriched
GROUP BY imputation_flag, imputation_flag_description
ORDER BY row_count DESC;
