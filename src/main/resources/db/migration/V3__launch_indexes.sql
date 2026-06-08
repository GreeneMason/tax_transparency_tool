CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Accelerates ILIKE searches for government and county name lookups.
CREATE INDEX IF NOT EXISTS idx_dim_government_unit_name_trgm
    ON govlens.dim_government_unit USING GIN (unit_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_dim_government_county_name_trgm
    ON govlens.dim_government_unit USING GIN (county_name gin_trgm_ops);

-- Supports ZIP endpoint filtering and ranking by HUD ratio.
CREATE INDEX IF NOT EXISTS idx_zip_lookup_zip_state_ratio_unit
    ON govlens.zip_to_unit_lookup (zip_code, state_fips, hud_ratio DESC, unit_id);

-- Covers high-frequency year/item filtering used by tax status checks.
CREATE INDEX IF NOT EXISTS idx_fact_year_item_amount
    ON govlens.fact_finance_unit_item_year (year, item_code, amount_thousands);

-- Helps per-unit yearly lookups for comparison and expense endpoints.
CREATE INDEX IF NOT EXISTS idx_fact_unit_year_item
    ON govlens.fact_finance_unit_item_year (unit_id, year, item_code);
