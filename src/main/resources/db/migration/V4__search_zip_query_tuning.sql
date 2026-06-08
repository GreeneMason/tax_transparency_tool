-- Search and ZIP query optimization for high-cardinality predicates.
-- Goal: Make trigram and composite indexes more attractive to planner.

-- Analyze dimensions to refresh planner statistics after initial loads.
ANALYZE govlens.dim_government_unit;
ANALYZE govlens.zip_to_unit_lookup;
ANALYZE govlens.fact_finance_unit_item_year;

-- Explicit partial index for WA-only searches (common case).
-- Helps avoid redundant state join in typical queries.
CREATE INDEX IF NOT EXISTS idx_dim_government_unit_wa_name_trgm
    ON govlens.dim_government_unit USING GIN (unit_name gin_trgm_ops)
    WHERE state_fips = '53';

CREATE INDEX IF NOT EXISTS idx_dim_government_unit_wa_county_trgm
    ON govlens.dim_government_unit USING GIN (county_name gin_trgm_ops)
    WHERE state_fips = '53';

-- Composite index for ZIP + state filtering (improves early filtering).
CREATE INDEX IF NOT EXISTS idx_zip_lookup_zip_state
    ON govlens.zip_to_unit_lookup (zip_code, state_fips);

-- Stats hint: tell planner that government_unit is smaller than it may estimate.
-- Helps with index-scan path decisions when filtering by state first.
ALTER TABLE govlens.dim_government_unit ALTER COLUMN state_fips SET STATISTICS 100;
ALTER TABLE govlens.dim_government_unit ALTER COLUMN unit_name SET STATISTICS 100;
ALTER TABLE govlens.zip_to_unit_lookup ALTER COLUMN zip_code SET STATISTICS 100;
ALTER TABLE govlens.zip_to_unit_lookup ALTER COLUMN state_fips SET STATISTICS 100;

-- Refresh statistics after tuning.
ANALYZE govlens.dim_government_unit;
ANALYZE govlens.zip_to_unit_lookup;
