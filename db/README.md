# GovLens PostgreSQL Setup (Washington MVP)

## 1) Apply schema

Run the migration SQL in your PostgreSQL database:

`psql -d <database_name> -f db/migration/V1__initial_govlens_schema.sql`

## 2) Load Washington CSV into staging

Generate the CSV first (if needed):

`C:/Users/Smokable/Documents/GitHub/tax_transparency_tool/.venv/Scripts/python.exe scripts/parse_finestdat.py`

Then run the `\copy` command shown in `db/load_wa_finance.sql` to import:

`psql -d <database_name>`

Inside psql, execute the `\copy` block from `db/load_wa_finance.sql`.

## 3) Promote staging data to dimensions/facts

`psql -d <database_name> -f db/load_wa_finance.sql`

## 4) Query the enriched view

`SELECT * FROM govlens.vw_finance_enriched LIMIT 50;`
