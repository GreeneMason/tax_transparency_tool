# GovLens Data Load Procedure

## Purpose
Define the release process for dataset updates, including frequency, execution steps, validation gates, and ownership.

## Release Cadence
- **Frequency:** Monthly or quarterly (aligned with Census Bureau publication cycles)
- **Maintenance window:** Non-business hours (weekends/early mornings) to minimize user impact
- **Rollback available:** Yes, via backup restore procedure (see `BACKUP_RESTORE_RUNBOOK.md`)

## Data Load Pipeline

### Step 1: Parse Source Files
- Input: Census 2023FinEstDAT fixed-width file, PID file, legend CSVs
- Output: `data/output/finestdat_2023_us_enriched.csv`
- Command: `python scripts/parse_finestdat.py --finestdat-path ... --pid-path ... --output-csv ...`

### Step 2: Stage Load
- Truncates and reloads `govlens.stg_finance_unit_item` from the enriched CSV
- Command: `psql -d govlens -c "TRUNCATE TABLE govlens.stg_finance_unit_item; \copy ..."`

### Step 3: Promote to Dimensions & Facts
- Inserts or updates dimensions (`dim_state`, `dim_gov_type`, `dim_item_code`, etc.)
- Upserts fact table (`fact_finance_unit_item_year`) with `ON CONFLICT` handling
- Command: `psql -d govlens -f db/load_finance.sql`

### Step 4: Validation Gates
- Runs `db/validate_release_gate.sql` which enforces:
  - Fact table is non-empty
  - No duplicate natural keys (unit_id, year, item_code)
  - All facts have valid dimension references
  - All government units have non-null names
- Failure blocks the release

### Step 5: Generate Report
- Produces a JSON report with:
  - Timestamp, status (success/failure)
  - Row counts for each table
  - Any warnings or errors encountered
  - Saved to `data/output/data_load_report_YYYYMMDD_HHMMSS.json`

## Execution

### Automated (Recommended)
```bash
python scripts/run_full_data_load.py \
  --workspace-root . \
  --db-user postgres \
  --db-name govlens \
  --db-password <password>
```

Exit code `0` = success; any other code = failure (check report JSON for details).

### Manual Steps (If Needed)
1. Parse: `python scripts/parse_finestdat.py --finestdat-path data/2023FinEstDAT_06052025modp_pu.txt --pid-path data/Fin_PID_2023.txt --legends-dir data/legends --output-csv data/output/finestdat_2023_us_enriched.csv --include-pid-fields`
2. Stage: `psql -d govlens < db/load_finance.sql` (includes the \copy command)
3. Validate: `psql -d govlens -f db/validate_release_gate.sql`
4. Check logs and report file

## Failure Handling

If any step fails:
1. Check the JSON report: `data/output/data_load_report_*.json`
2. Review error messages in the report
3. Do NOT promote to production if gates fail
4. Fix source data or SQL issues, then retry

## Rollback

If data integrity issues are discovered after a load:
1. Follow `db/BACKUP_RESTORE_RUNBOOK.md` to restore from the previous known-good backup
2. Notify stakeholders of the incident
3. Investigate root cause before re-attempting the load

## Ownership

- **Primary owner:** Data platform engineer / DBA
  - Responsible for scheduling, executing, and monitoring loads
  - Owns validation gates and rollback decisions
- **Secondary owner:** Backend engineer on call
  - On-call escalation if load fails during business hours
  - Assist with incident response and rollback

## Monitoring

### Post-Load Checks
- Verify `/health` endpoint reports `UP` after deployment
- Spot-check API responses (search, comparison, expense breakdown)
- Monitor logs for data-access errors in first 2 hours

### Metrics to Track
- Load duration (parsing + staging + promotion)
- Row counts (staging → dimensions → facts)
- Validation gate pass/fail
- Time to rollback (if needed)

## Change Log
- 2026-06-06: Initial release procedure documented
