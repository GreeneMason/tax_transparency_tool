-- Example load command in psql (run manually):
-- \copy govlens.stg_zip_to_unit_lookup(
--   zip_code,state_fips,county_fips,county_fips_full,unit_id,hud_ratio,match_scope,source_file
-- )
-- FROM 'data/output/us_zip_to_unit_lookup.csv'
-- WITH (FORMAT csv, HEADER true);

DELETE FROM govlens.zip_to_unit_lookup target
USING (
  SELECT DISTINCT state_fips
  FROM govlens.stg_zip_to_unit_lookup
  WHERE state_fips IS NOT NULL
) staged
WHERE target.state_fips = staged.state_fips;

INSERT INTO govlens.zip_to_unit_lookup (
    zip_code,
    unit_id,
    state_fips,
    county_fips,
    county_fips_full,
    hud_ratio,
    match_scope,
    source_file,
    loaded_at
)
SELECT
    s.zip_code,
    s.unit_id,
    s.state_fips,
    NULLIF(s.county_fips, ''),
    NULLIF(s.county_fips_full, ''),
    s.hud_ratio,
    NULLIF(s.match_scope, ''),
    NULLIF(s.source_file, ''),
    NOW()
FROM govlens.stg_zip_to_unit_lookup s
JOIN govlens.dim_government_unit g ON g.unit_id = s.unit_id
WHERE s.state_fips IS NOT NULL
  AND s.zip_code IS NOT NULL
  AND s.unit_id IS NOT NULL
ON CONFLICT (zip_code, unit_id) DO UPDATE
SET state_fips = EXCLUDED.state_fips,
    county_fips = EXCLUDED.county_fips,
    county_fips_full = EXCLUDED.county_fips_full,
    hud_ratio = EXCLUDED.hud_ratio,
    match_scope = EXCLUDED.match_scope,
    source_file = EXCLUDED.source_file,
    loaded_at = NOW();
