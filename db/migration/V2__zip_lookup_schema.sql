CREATE TABLE IF NOT EXISTS govlens.stg_zip_to_unit_lookup (
    zip_code CHAR(5),
    state_fips CHAR(2),
    county_fips CHAR(3),
    county_fips_full CHAR(5),
    unit_id CHAR(12),
    hud_ratio NUMERIC(10, 6),
    match_scope TEXT,
    source_file TEXT,
    loaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS govlens.zip_to_unit_lookup (
    zip_code CHAR(5) NOT NULL,
    unit_id CHAR(12) NOT NULL REFERENCES govlens.dim_government_unit(unit_id),
    state_fips CHAR(2) NOT NULL REFERENCES govlens.dim_state(state_fips),
    county_fips CHAR(3),
    county_fips_full CHAR(5),
    hud_ratio NUMERIC(10, 6),
    match_scope TEXT,
    source_file TEXT,
    loaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_zip_to_unit_lookup PRIMARY KEY (zip_code, unit_id)
);

CREATE INDEX IF NOT EXISTS idx_zip_lookup_zip ON govlens.zip_to_unit_lookup(zip_code);
CREATE INDEX IF NOT EXISTS idx_zip_lookup_unit ON govlens.zip_to_unit_lookup(unit_id);
