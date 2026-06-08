# Phase 1 Index Tuning Report

## Scope
Validate that Phase 1 launch indexes align with key API query patterns and identify any remaining tuning work.

## Artifacts
- Query-plan script: `db/explain_analyze_phase1.sql`
- Index migration: `src/main/resources/db/migration/V3__launch_indexes.sql`

## Endpoints and Query Patterns Reviewed
- `GET /api/v1/governments` (ILIKE search over unit/county names)
- `GET /api/v1/governments/by-zip` (ZIP + state + ratio sort)
- `GET /api/v1/governments/{unitId}/income-tax-status` (year/item/amount filter)
- `GET /api/v1/compare` (per-unit/year item joins)
- `GET /api/v1/governments/{unitId}/expense-breakdown` (per-unit/year category aggregates)

## Expected Index Coverage
- Search acceleration:
  - `idx_dim_government_unit_name_trgm`
  - `idx_dim_government_county_name_trgm`
- ZIP lookup coverage:
  - `idx_zip_lookup_zip_state_ratio_unit`
- Fact filters and unit-year lookups:
  - `idx_fact_year_item_amount`
  - `idx_fact_unit_year_item`

## Execution Results (Current Workspace)
Executed command:
```bash
"C:\\Program Files\\PostgreSQL\\17\\bin\\psql.exe" -U postgres -d govlens -v ON_ERROR_STOP=1 -P pager=off -f db/explain_analyze_phase1.sql > db/explain_analyze_phase1_output.txt
```

Output artifact:
- `db/explain_analyze_phase1_output.txt`

Measured highlights:
- Search query (`ILIKE '%sea%'`) ran in ~94.5 ms and used `Seq Scan` on `dim_government_unit`.
- ZIP lookup query ran in ~0.9 ms and used `idx_zip_lookup_zip` with top-N sort.
- Income-tax status query ran in ~51.8 ms and used `uq_fact_unit_item_year` index probes; no fact-table sequential scan.
- Comparison query ran in ~4.9 ms and used `idx_fact_unit_id` for both units.
- Expense breakdown query ran in ~1.2 ms and used `idx_fact_unit_id` for selected unit.

## Plan-Level Checklist
- [ ] Search query uses trigram index path for ILIKE predicates.
- [ ] ZIP lookup query uses composite ZIP/state/rank-friendly access path.
- [x] Income-tax query avoids broad sequential scans on fact table.
- [x] Comparison query uses `unit_id, year` access path consistently.
- [x] Expense query uses `unit_id, year` access path consistently.

## Preliminary Verdict
- Status: Completed with follow-up tuning recommended
- Reason: Query plans were successfully captured, and fact-table access paths are acceptable for current data volume. Two non-blocking opportunities remain for search and ZIP-ordering path improvements.

## Recommended Next Action
1. Add a follow-up migration `V4__search_zip_query_tuning.sql` to improve planner path selection for search and ZIP ranking.
2. Update API predicates to make filters sargable and avoid OR conditions across joined tables:
   - Prefer filtering by `g.state_fips` only (normalize state filter before SQL), then join `dim_state`.
   - Split search OR predicates into `UNION ALL` branches or dedicated exact/ILIKE branches.
3. Re-run `db/explain_analyze_phase1.sql` after query updates and confirm trigram/composite index usage.
