# Project: GovLens (Launch Roadmap)

## Mission
Turn public finance files into clear, searchable, and trustworthy civic data for everyday residents.

## Current Baseline (June 2026)
- Data model is live in PostgreSQL with dimensions, fact table, and enriched view (`govlens.vw_finance_enriched`).
- ZIP-to-government lookup pipeline exists (`govlens.zip_to_unit_lookup`) and powers `/api/v1/governments/by-zip`.
- Core API endpoints are implemented: health, search, ZIP lookup, comparison, and expense breakdown.
- App has production-oriented env config (rate limits, CORS controls, graceful shutdown), but deployment automation and production hardening are still pending.

## Database Review Summary
- Good: clear star-schema direction and idempotent load scripts (`ON CONFLICT` upserts).
- Good: validation SQL exists for load correctness checks.
- Gap: migrations are present but Flyway is disabled by default (`GOVLENS_FLYWAY_ENABLED=false`).
- Gap: load + validation are manual steps today (not yet enforced in CI/CD).
- Gap: search and ZIP lookup query patterns can benefit from targeted production indexes and `EXPLAIN ANALYZE` verification.
- Gap: backup/restore, retention, and rollout/rollback runbooks are not documented as release gates.

## Web Launch Roadmap

### Phase 1: Production DB Hardening (Week 1) ✅ COMPLETE
- [x] Enable Flyway in non-dev deploys and run migrations automatically on startup.
- [x] Add a release-safe migration for performance indexes used by live queries:
	- [x] `dim_government_unit` name search acceleration (`ILIKE` support, e.g. trigram index).
	- [x] `zip_to_unit_lookup` composite index for `zip_code` + `state_fips` + rank ordering.
	- [x] Validate existing fact-table indexes against comparison/expense query plans.
- [x] Add a lightweight data-quality gate SQL script for deploys (row counts, referential checks, null thresholds).
- [x] Document backup and restore SOP (daily snapshot, restore test, RPO/RTO targets).
- [x] Add follow-up search/ZIP query optimization (V4 migration + sargable repository filters).

### Phase 2: Reliable Data Refresh Pipeline (Week 1-2) ✅ COMPLETE
- [x] Create one command/script to run: parse -> stage load -> dimension/fact upsert -> validation report.
- [x] Produce a machine-readable load report (counts, warnings, failures) saved under `data/output/`.
- [x] Add failure conditions that block release (duplicate fact keys > 0, missing dims > 0, etc.).
- [x] Define release cadence for dataset updates (monthly/quarterly) and ownership.

### Phase 3: API Readiness for Public Traffic (Week 2) ✅ COMPLETE
- [x] Add pagination metadata and stable sorting guarantees for list endpoints.
  - [x] Created `PaginatedResponse<T>` wrapper with `data` and `pagination` metadata (limit, offset, total_count, has_more).
  - [x] Updated `/api/v1/governments` and `/api/v1/governments/by-zip` endpoints with limit/offset parameters.
  - [x] Enforce limit bounds: 1-100 entries per page, offset >= 0.
  - [x] Stable ordering: search results rank by exact-match, then alphabetical; ZIP results by HUD ratio DESC, population DESC, name.
- [x] Add versioned API contract checks (snapshot tests for key response schemas).
  - [x] Created `src/test/resources/api-contracts/README.md` documenting contract snapshot structure and maintenance.
  - [x] Contract validation via integration tests ensures response fields are present and data types are correct.
- [x] Add integration tests against a seeded Postgres instance for:
  - [x] `/api/v1/governments` (query validation, state filter, pagination limit enforcement).
  - [x] `/api/v1/governments/by-zip` (ZIP validation, results ranking, pagination).
  - [x] `/api/v1/governments/{unitId}/expense-breakdown` (endpoint contract, category structure).
  - [x] `/api/v1/compare` (side-by-side schema validation, difference calculation).
- [x] Add structured request logging (request id, latency, status code, endpoint).
  - [x] Created `StructuredLoggingFilter` servlet filter with X-Request-ID header propagation.
  - [x] Logs: endpoint, method, query string, status, latency_ms in SLF4J.
  - [x] Supports MDC propagation for downstream trace correlation.

### Phase 4: Frontend and UX Launch Pass (Week 2-3) ✅ COMPLETE
- [x] Move from MVP page to a production UI build pipeline (bundle, minify, cache busting).
  - [x] Created webpack config with Terser (JS minification), CSS Minimizer (style optimization)
  - [x] Content-hash filenames (`[contenthash:8]`) for automatic cache busting
  - [x] Code splitting: app + vendors + runtime for optimal caching
  - [x] Critical CSS inlining in index.html to prevent FOUC (Flash of Unstyled Content)
  - [x] Maven integration via frontend-maven-plugin for CI/CD build automation
- [x] Add user-visible loading, empty-state, and error states for all API-backed panels.
  - [x] AppState class manages: searchQuery, searchResults, loadingState, currentPage, totalResults
  - [x] UIRenderer generates HTML for: loading spinner, error card with retry, empty state, result cards
  - [x] State flow: idle → loading (spinner) → success (results) or error (alert with retry)
  - [x] Real-time feedback for search input, pagination updates, filter changes
- [x] Add accessibility pass (keyboard nav, labels, contrast, focus states).
  - [x] WCAG 2.1 AA compliance: 4.5:1 text contrast, 3:1 UI element contrast
  - [x] Full keyboard navigation (Tab through all buttons, Enter to submit)
  - [x] Focus indicators: 2px outline with 2px offset, visible on all interactive elements
  - [x] Semantic HTML: `<form>`, `<button>`, `<input>` with associated `<label>` or aria-label
  - [x] Screen reader support: aria-label, aria-live="polite" for dynamic updates, role="status" for loading
  - [x] Reduced motion support: `@media (prefers-reduced-motion: reduce)` disables animations
  - [x] Skip link for keyboard users to bypass search and go to results
  - [x] 200% zoom compatibility: responsive design supports text enlargement
- [x] Add analytics-safe event instrumentation (search, compare, zip lookup usage).
  - [x] Privacy-first analytics: no PII collected, no third-party trackers
  - [x] Events tracked: search (queryLength, resultCount), result clicks (hashed unitId), page views, errors
  - [x] Batched transmission: auto-flush every 30 events or 60 seconds
  - [x] Offline support: event queue persists while offline, auto-resend on reconnection
  - [x] Keepalive flag: ensures analytics complete even if user closes browser tab

### Phase 5: Deployment and Operations (Week 3)
- [ ] Choose target hosting stack (Render/Fly.io/Railway/Azure/AWS) and codify infra settings.
- [ ] Add CI pipeline stages:
	- [ ] build + unit tests
	- [ ] DB migration check
	- [ ] API integration tests
	- [ ] artifact publish
- [ ] Create CD workflow with separate staging and production environments.
- [ ] Add environment secret management for DB credentials and CORS origin lists.
- [ ] Configure uptime checks and alerting from `/health`.

### Phase 6: Launch Gate and Go-Live (Week 4)
- [ ] Run load/perf test for top endpoints and set baseline SLOs.
- [ ] Conduct security pass (dependency scan, SQL-injection checks, CORS policy verification).
- [ ] Publish operational runbooks:
	- [ ] incident response
	- [ ] rollback procedure
	- [ ] data refresh procedure
- [ ] Soft launch (limited audience) for 3-7 days.
- [ ] Public launch with monitoring dashboard and daily triage routine for first 2 weeks.

## Post-Launch Expansion
- [ ] Enable full US refresh cadence once WA launch metrics are stable.
- [ ] Add resident-focused explainers (glossary and source lineage per metric).
- [ ] Add export/share artifacts for civic transparency reports.

## Tech Stack
- Backend: Java 17, Spring Boot
- Database: PostgreSQL
- Data Pipeline: Python scripts + SQL load scripts
- Frontend: Static web app (current), production build pipeline planned
- Source Data: U.S. Census 2023 Annual Survey of State and Local Government Finances