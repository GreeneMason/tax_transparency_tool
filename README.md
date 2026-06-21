# GovLens

Turn public U.S. Census finance files into clear, searchable, and trustworthy civic data for everyday residents.

GovLens ingests the [Annual Survey of State and Local Government Finances](https://www.census.gov/programs-surveys/gov-finances.html) (2023 public-use files) into a PostgreSQL database and exposes it via a Spring Boot REST API and a static frontend that includes a county-level choropleth map.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend API | Java 17, Spring Boot |
| Database | PostgreSQL (primary), SQLite (map read-only export) |
| Data Pipeline | Python scripts + SQL load scripts |
| Frontend | Static HTML/CSS/JS; production build via Webpack + Maven |
| Map | MapLibre GL JS, `sql.js-httpvfs`, US Census TIGER county boundaries |
| Hosting | EC2 (API + app), S3 (static assets + SQLite map database) |

---

## Project Status

| Phase | Description | Status |
|---|---|---|
| 1 | Production DB hardening (Flyway, indexes, backup SOP) | ✅ Complete |
| 2 | Reliable data refresh pipeline | ✅ Complete |
| 3 | API readiness (pagination, contract tests, integration tests, structured logging) | ✅ Complete |
| 4 | Frontend and UX launch pass (Webpack build, accessibility, analytics) | ✅ Complete |
| 5 | Deployment and CI/CD | 🔲 Pending |
| 6 | Launch gate and go-live | 🔲 Pending |
| 7.1 | SQLite database export for map | ✅ Complete |
| 7.2 | S3 hosting and CORS configuration | 🔲 Pending |
| 7.3 | Interactive choropleth map frontend | ✅ Complete |
| 7.4 | Map performance and validation | 🔲 Pending |

---

## Prerequisites

- Java 17+
- Maven 3.9+
- PostgreSQL 16+
- Python 3.x (for data pipeline scripts)

---

## Running Locally

### 1. Start the API

```bash
mvn spring-boot:run
```

Defaults to `http://localhost:8080`. Override with environment variables:

| Variable | Default |
|---|---|
| `GOVLENS_DB_URL` | `jdbc:postgresql://localhost:5432/govlens` |
| `GOVLENS_DB_USER` | `postgres` |
| `GOVLENS_DB_PASSWORD` | `postgres` |
| `PORT` | `8080` |
| `GOVLENS_ENV` | `dev` |
| `GOVLENS_FLYWAY_ENABLED` | `false` |
| `GOVLENS_RATE_LIMIT_ENABLED` | `true` |
| `GOVLENS_RATE_LIMIT_REQUESTS_PER_WINDOW` | `120` |
| `GOVLENS_RATE_LIMIT_WINDOW_SECONDS` | `60` |

> In production (`GOVLENS_ENV=prod`), wildcard CORS origins are blocked at startup.

### 2. Build the frontend

```bash
mvn package
```

This runs Webpack (via `frontend-maven-plugin`) to bundle and minify the static assets with content-hash filenames for cache busting.

---

## Database Setup

See [db/README.md](db/README.md) for full step-by-step instructions. Quick summary:

```bash
# 1. Apply schema migrations
psql -d govlens -f db/migration/V1__initial_govlens_schema.sql
psql -d govlens -f db/migration/V2__zip_lookup_schema.sql

# 2. Parse raw Census file into enriched CSV
python scripts/parse_finestdat.py

# 3. Load finance data
psql -d govlens -f db/load_finance.sql

# 4. Build and load ZIP-to-unit lookup (requires HUD USPS crosswalk file)
python scripts/build_zip_unit_lookup.py --hud-zip-county <path_to_hud_file>
psql -d govlens -f db/load_zip_lookup.sql

# 5. Run one full data refresh (parse → load → validate → SQLite export)
python scripts/run_full_data_load.py
```

---

## API Endpoints

Base URL: `http://localhost:8080`

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Health check (database connectivity) |
| GET | `/api/v1/governments` | Search governments by name (`?query=`, `?state=`, `?limit=`, `?offset=`) |
| GET | `/api/v1/governments/by-zip` | Look up governments by ZIP code (`?zip=`, `?state=`, `?limit=`, `?offset=`) |
| GET | `/api/v1/governments/{unitId}/income-tax-status` | Whether a government collects income tax |
| GET | `/api/v1/governments/{unitId}/expense-breakdown` | Expense breakdown by category |
| GET | `/api/v1/compare` | Side-by-side comparison of two governments |
| GET | `/api/v1/map/item-codes` | All expense category codes (for map dropdown) |
| GET | `/api/v1/map/gov-types` | All government type codes (for map layer dropdown) |
| GET | `/api/v1/map/county-spending` | County-level aggregated spending (`?itemCode=`, `?year=`, `?govTypeCode=`) |

See [API.md](API.md) for full request/response schemas and examples.

---

## Interactive Choropleth Map

`map.html` provides a county-level spending map powered by a SQLite file served via HTTP range requests (no server query per interaction).

**Controls:**
- **Government Layer** — filter by government type (All, County, City, Special District, etc.)
- **Expense Category** — select a specific item code or aggregate all
- **Year** — any year present in the data
- **Metric** — total spending or per-capita spending

**How it works:**  
`scripts/export_sqlite_map.py` exports `govlens.vw_finance_enriched` to a compact SQLite file with indexes on `county_fips`, `state_fips`, `item_code`, `gov_type_code`, and `year`. The browser uses `sql.js-httpvfs` (WebAssembly SQLite + HTTP Range Requests) to query only the needed index blocks — typically under 200 KB per query rather than downloading the full file.

---

## Data Pipeline Scripts

| Script | Purpose |
|---|---|
| `scripts/parse_finestdat.py` | Parse raw Census fixed-width file → enriched CSV |
| `scripts/parse_tech_docs_legends.py` | Extract legend CSVs from Census technical PDF |
| `scripts/build_zip_unit_lookup.py` | Build ZIP-to-government crosswalk from HUD USPS data |
| `scripts/export_sqlite_map.py` | Export PostgreSQL finance data → SQLite map database |
| `scripts/run_full_data_load.py` | Orchestrate full pipeline: parse → load → validate → export |

---

## Running Tests

```bash
mvn test
```

Integration tests (`ApiContractIT`) require a running PostgreSQL instance seeded with test data. Contract snapshots live in `src/test/resources/api-contracts/`.

---

## Data Source

U.S. Census Bureau — [2023 Annual Survey of State and Local Government Finances](https://www.census.gov/programs-surveys/gov-finances.html), public-use files.
