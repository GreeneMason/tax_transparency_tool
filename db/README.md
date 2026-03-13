# GovLens PostgreSQL Setup (Washington MVP)

## 1) Apply schema

Run the migration SQL in your PostgreSQL database:

`psql -d <database_name> -f db/migration/V1__initial_govlens_schema.sql`

Then apply ZIP lookup schema additions:

`psql -d <database_name> -f db/migration/V2__zip_lookup_schema.sql`

## 2) Load Washington CSV into staging

Generate the CSV first (if needed):

`C:/Users/Smokable/Documents/GitHub/tax_transparency_tool/.venv/Scripts/python.exe scripts/parse_finestdat.py`

Then run the `\copy` command shown in `db/load_wa_finance.sql` to import:

`psql -d <database_name>`

Inside psql, run:

`TRUNCATE TABLE govlens.stg_finance_unit_item;`

Then execute the `\copy` block from `db/load_wa_finance.sql`.

## 3) Promote staging data to dimensions/facts

`psql -d <database_name> -f db/load_wa_finance.sql`

## 4) Query the enriched view

`SELECT * FROM govlens.vw_finance_enriched LIMIT 50;`

## 5) Run load validation checks

`psql -d <database_name> -f db/validate_wa_load.sql`

## 6) Build ZIP to unit lookup from HUD crosswalk

1. Download the HUD-USPS ZIP-COUNTY crosswalk CSV (or ZIP containing CSV).
2. Build Washington ZIP-to-unit rows:

`C:/Users/Smokable/Documents/GitHub/tax_transparency_tool/.venv/Scripts/python.exe scripts/build_zip_unit_lookup.py --hud-zip-county <path_to_hud_zip_county_file>`

This generates `data/output/wa_zip_to_unit_lookup.csv`.

## 7) Load ZIP lookup into PostgreSQL

In `psql`, run:

`TRUNCATE TABLE govlens.stg_zip_to_unit_lookup;`

Then execute the `\copy` block in `db/load_zip_lookup.sql`, then:

`psql -d <database_name> -f db/load_zip_lookup.sql`

After this, `/api/v1/governments/by-zip?zip=98101` returns crosswalk-backed governments.
