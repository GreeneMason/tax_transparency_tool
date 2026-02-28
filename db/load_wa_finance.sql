TRUNCATE TABLE govlens.stg_finance_unit_item;

-- Example load command in psql (run manually):
-- \copy govlens.stg_finance_unit_item(
--   unit_id,state_fips,state_abbrev,state_name,gov_type_code,gov_type_description,
--   county_fips,unit_identifier,item_code,item_description,amount,year,
--   imputation_flag,imputation_flag_description,unit_name,county_name,place_fips,
--   population,function_code
-- )
-- FROM 'data/output/finestdat_2023_wa_enriched.csv'
-- WITH (FORMAT csv, HEADER true);

INSERT INTO govlens.dim_state (state_fips, state_abbrev, state_name)
SELECT DISTINCT state_fips, state_abbrev, state_name
FROM govlens.stg_finance_unit_item
WHERE state_fips IS NOT NULL
ON CONFLICT (state_fips) DO UPDATE
SET state_abbrev = EXCLUDED.state_abbrev,
    state_name = EXCLUDED.state_name;

INSERT INTO govlens.dim_gov_type (gov_type_code, description)
SELECT DISTINCT gov_type_code, gov_type_description
FROM govlens.stg_finance_unit_item
WHERE gov_type_code IS NOT NULL
ON CONFLICT (gov_type_code) DO UPDATE
SET description = EXCLUDED.description;

INSERT INTO govlens.dim_item_code (item_code, description)
SELECT DISTINCT item_code, item_description
FROM govlens.stg_finance_unit_item
WHERE item_code IS NOT NULL
ON CONFLICT (item_code) DO UPDATE
SET description = EXCLUDED.description;

INSERT INTO govlens.dim_imputation_flag (flag, description)
SELECT DISTINCT imputation_flag, imputation_flag_description
FROM govlens.stg_finance_unit_item
WHERE imputation_flag IS NOT NULL
ON CONFLICT (flag) DO UPDATE
SET description = EXCLUDED.description;

INSERT INTO govlens.dim_function_code (function_code, description)
SELECT DISTINCT s.function_code, f.description
FROM govlens.stg_finance_unit_item s
LEFT JOIN govlens.dim_function_code f ON f.function_code = s.function_code
WHERE s.function_code IS NOT NULL
ON CONFLICT (function_code) DO NOTHING;

INSERT INTO govlens.dim_government_unit (
    unit_id,
    state_fips,
    gov_type_code,
    county_fips,
    unit_identifier,
    unit_name,
    county_name,
    place_fips,
    population,
    function_code
)
SELECT DISTINCT
    unit_id,
    state_fips,
    gov_type_code,
    county_fips,
    unit_identifier,
    unit_name,
    county_name,
    NULLIF(place_fips, ''),
    NULLIF(population, 0),
    NULLIF(function_code, '')
FROM govlens.stg_finance_unit_item
WHERE unit_id IS NOT NULL
ON CONFLICT (unit_id) DO UPDATE
SET state_fips = EXCLUDED.state_fips,
    gov_type_code = EXCLUDED.gov_type_code,
    county_fips = EXCLUDED.county_fips,
    unit_identifier = EXCLUDED.unit_identifier,
    unit_name = EXCLUDED.unit_name,
    county_name = EXCLUDED.county_name,
    place_fips = EXCLUDED.place_fips,
    population = EXCLUDED.population,
    function_code = EXCLUDED.function_code;

INSERT INTO govlens.fact_finance_unit_item_year (
    unit_id,
    year,
    item_code,
    amount_thousands,
    imputation_flag
)
SELECT
    s.unit_id,
    s.year,
    s.item_code,
    s.amount,
    s.imputation_flag
FROM govlens.stg_finance_unit_item s
ON CONFLICT (unit_id, year, item_code) DO UPDATE
SET amount_thousands = EXCLUDED.amount_thousands,
    imputation_flag = EXCLUDED.imputation_flag,
    loaded_at = NOW();
