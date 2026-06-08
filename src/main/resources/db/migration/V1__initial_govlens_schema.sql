CREATE SCHEMA IF NOT EXISTS govlens;

CREATE TABLE IF NOT EXISTS govlens.dim_state (
    state_fips CHAR(2) PRIMARY KEY,
    state_abbrev TEXT NOT NULL,
    state_name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS govlens.dim_gov_type (
    gov_type_code CHAR(1) PRIMARY KEY,
    description TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS govlens.dim_item_code (
    item_code CHAR(3) PRIMARY KEY,
    description TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS govlens.dim_function_code (
    function_code CHAR(2) PRIMARY KEY,
    description TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS govlens.dim_imputation_flag (
    flag CHAR(1) PRIMARY KEY,
    description TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS govlens.dim_government_unit (
    unit_id CHAR(12) PRIMARY KEY,
    state_fips CHAR(2) NOT NULL REFERENCES govlens.dim_state(state_fips),
    gov_type_code CHAR(1) NOT NULL REFERENCES govlens.dim_gov_type(gov_type_code),
    county_fips CHAR(3),
    unit_identifier CHAR(6),
    unit_name TEXT,
    county_name TEXT,
    place_fips CHAR(5),
    population BIGINT,
    function_code CHAR(2),
    school_level_code CHAR(2),
    fiscal_year_ending SMALLINT,
    survey_year CHAR(2),
    CONSTRAINT fk_government_function
        FOREIGN KEY (function_code)
        REFERENCES govlens.dim_function_code(function_code)
);

CREATE TABLE IF NOT EXISTS govlens.stg_finance_unit_item (
    unit_id CHAR(12),
    state_fips CHAR(2),
    state_abbrev TEXT,
    state_name TEXT,
    gov_type_code CHAR(1),
    gov_type_description TEXT,
    county_fips CHAR(3),
    unit_identifier CHAR(6),
    item_code CHAR(3),
    item_description TEXT,
    amount BIGINT,
    year SMALLINT,
    imputation_flag CHAR(1),
    imputation_flag_description TEXT,
    unit_name TEXT,
    county_name TEXT,
    place_fips CHAR(5),
    population BIGINT,
    function_code CHAR(2),
    loaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS govlens.fact_finance_unit_item_year (
    fact_id BIGSERIAL PRIMARY KEY,
    unit_id CHAR(12) NOT NULL REFERENCES govlens.dim_government_unit(unit_id),
    year SMALLINT NOT NULL,
    item_code CHAR(3) NOT NULL REFERENCES govlens.dim_item_code(item_code),
    amount_thousands BIGINT NOT NULL,
    imputation_flag CHAR(1) NOT NULL REFERENCES govlens.dim_imputation_flag(flag),
    loaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_fact_unit_item_year UNIQUE (unit_id, year, item_code)
);

CREATE INDEX IF NOT EXISTS idx_fact_year ON govlens.fact_finance_unit_item_year(year);
CREATE INDEX IF NOT EXISTS idx_fact_item_code ON govlens.fact_finance_unit_item_year(item_code);
CREATE INDEX IF NOT EXISTS idx_fact_unit_id ON govlens.fact_finance_unit_item_year(unit_id);
CREATE INDEX IF NOT EXISTS idx_dim_government_state ON govlens.dim_government_unit(state_fips);
CREATE INDEX IF NOT EXISTS idx_dim_government_name ON govlens.dim_government_unit(unit_name);

CREATE OR REPLACE VIEW govlens.vw_finance_enriched AS
SELECT
    f.fact_id,
    f.year,
    g.unit_id,
    g.unit_name,
    g.county_name,
    g.place_fips,
    s.state_fips,
    s.state_abbrev,
    s.state_name,
    g.gov_type_code,
    gt.description AS gov_type_description,
    f.item_code,
    ic.description AS item_description,
    f.amount_thousands,
    f.imputation_flag,
    fl.description AS imputation_flag_description,
    g.population,
    g.function_code,
    fc.description AS function_description
FROM govlens.fact_finance_unit_item_year f
JOIN govlens.dim_government_unit g ON g.unit_id = f.unit_id
JOIN govlens.dim_state s ON s.state_fips = g.state_fips
JOIN govlens.dim_gov_type gt ON gt.gov_type_code = g.gov_type_code
JOIN govlens.dim_item_code ic ON ic.item_code = f.item_code
JOIN govlens.dim_imputation_flag fl ON fl.flag = f.imputation_flag
LEFT JOIN govlens.dim_function_code fc ON fc.function_code = g.function_code;
