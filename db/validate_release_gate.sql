-- Release gate checks for deployment pipelines.
-- Fail fast if critical data integrity constraints are violated.

DO $$
DECLARE
    fact_rows BIGINT;
    duplicate_fact_keys BIGINT;
    facts_missing_unit BIGINT;
    facts_missing_item BIGINT;
    facts_missing_flag BIGINT;
    missing_unit_names BIGINT;
BEGIN
    SELECT COUNT(*) INTO fact_rows
    FROM govlens.fact_finance_unit_item_year;

    IF fact_rows = 0 THEN
        RAISE EXCEPTION 'Release gate failed: fact table has zero rows.';
    END IF;

    SELECT COUNT(*) INTO duplicate_fact_keys
    FROM (
        SELECT unit_id, year, item_code
        FROM govlens.fact_finance_unit_item_year
        GROUP BY unit_id, year, item_code
        HAVING COUNT(*) > 1
    ) d;

    IF duplicate_fact_keys > 0 THEN
        RAISE EXCEPTION 'Release gate failed: duplicate fact keys found (%).', duplicate_fact_keys;
    END IF;

    SELECT COUNT(*) INTO facts_missing_unit
    FROM govlens.fact_finance_unit_item_year f
    LEFT JOIN govlens.dim_government_unit g ON g.unit_id = f.unit_id
    WHERE g.unit_id IS NULL;

    IF facts_missing_unit > 0 THEN
        RAISE EXCEPTION 'Release gate failed: facts missing government unit references (%).', facts_missing_unit;
    END IF;

    SELECT COUNT(*) INTO facts_missing_item
    FROM govlens.fact_finance_unit_item_year f
    LEFT JOIN govlens.dim_item_code i ON i.item_code = f.item_code
    WHERE i.item_code IS NULL;

    IF facts_missing_item > 0 THEN
        RAISE EXCEPTION 'Release gate failed: facts missing item-code references (%).', facts_missing_item;
    END IF;

    SELECT COUNT(*) INTO facts_missing_flag
    FROM govlens.fact_finance_unit_item_year f
    LEFT JOIN govlens.dim_imputation_flag fl ON fl.flag = f.imputation_flag
    WHERE fl.flag IS NULL;

    IF facts_missing_flag > 0 THEN
        RAISE EXCEPTION 'Release gate failed: facts missing imputation-flag references (%).', facts_missing_flag;
    END IF;

    SELECT COUNT(*) INTO missing_unit_names
    FROM govlens.dim_government_unit
    WHERE unit_name IS NULL OR btrim(unit_name) = '';

    IF missing_unit_names > 0 THEN
        RAISE EXCEPTION 'Release gate failed: government units missing names (%).', missing_unit_names;
    END IF;

    RAISE NOTICE 'Release gate passed: % fact rows validated.', fact_rows;
END $$;
