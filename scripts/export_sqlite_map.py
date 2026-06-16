#!/usr/bin/env python3
"""
GovLens SQLite Map Export — Phase 7.1

Exports the enriched government finance data from PostgreSQL into a SQLite
file optimized for sql.js-httpvfs HTTP Range Request queries.

Key design decisions:
  - PRAGMA page_size = 4096  (must be set before any data is inserted)
  - PRAGMA journal_mode = DELETE  (required; WAL produces a second file that
    breaks sql.js-httpvfs byte-range mapping)
  - Indexes are built AFTER bulk insert for speed, then VACUUM defragments
    the file so page offsets are contiguous and range reads stay small.

Output: data/output/govlens_map.sqlite3

Usage:
    python scripts/export_sqlite_map.py [options]

Options:
    --db-user        PostgreSQL user         (default: postgres / $DB_USER)
    --db-name        PostgreSQL database     (default: govlens  / $DB_NAME)
    --db-password    PostgreSQL password     (default: postgres / $PGPASSWORD)
    --output         Output SQLite path      (default: data/output/govlens_map.sqlite3)
    --workspace-root Workspace root          (default: cwd)
"""

from __future__ import annotations

import argparse
import csv
import os
import sqlite3
import subprocess
import sys
from datetime import datetime
from pathlib import Path


# ---------------------------------------------------------------------------
# SQL: PostgreSQL export query
# ---------------------------------------------------------------------------

EXPORT_QUERY = """
SELECT
    f.unit_id,
    g.unit_name,
    COALESCE(g.county_name, '')    AS county_name,
    COALESCE(g.county_fips, '')    AS county_fips,
    s.state_fips,
    s.state_abbrev,
    s.state_name,
    g.gov_type_code,
    gt.description                 AS gov_type_description,
    f.item_code,
    ic.description                 AS item_description,
    f.amount_thousands,
    COALESCE(g.population, 0)      AS population,
    f.year
FROM govlens.fact_finance_unit_item_year f
JOIN govlens.dim_government_unit  g  ON g.unit_id       = f.unit_id
JOIN govlens.dim_state            s  ON s.state_fips    = g.state_fips
JOIN govlens.dim_gov_type         gt ON gt.gov_type_code = g.gov_type_code
JOIN govlens.dim_item_code        ic ON ic.item_code    = f.item_code
ORDER BY f.unit_id, f.year, f.item_code
"""

# ---------------------------------------------------------------------------
# SQL: SQLite schema and indexes
# ---------------------------------------------------------------------------

CREATE_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS finance (
    unit_id              TEXT    NOT NULL,
    unit_name            TEXT    NOT NULL,
    county_name          TEXT    NOT NULL,
    county_fips          TEXT    NOT NULL,
    state_fips           TEXT    NOT NULL,
    state_abbrev         TEXT    NOT NULL,
    state_name           TEXT    NOT NULL,
    gov_type_code        TEXT    NOT NULL,
    gov_type_description TEXT    NOT NULL,
    item_code            TEXT    NOT NULL,
    item_description     TEXT    NOT NULL,
    amount_thousands     INTEGER NOT NULL,
    population           INTEGER NOT NULL,
    year                 INTEGER NOT NULL
)
"""

# Indexes are created after bulk insert so they don't slow down row writes.
# Order matters: most-selective first keeps the index file small.
INDEX_STATEMENTS = [
    ("idx_state_year_item",  "CREATE INDEX IF NOT EXISTS idx_state_year_item  ON finance(state_fips, year, item_code)"),
    ("idx_county_year_item", "CREATE INDEX IF NOT EXISTS idx_county_year_item ON finance(county_fips, year, item_code)"),
    ("idx_unit_year",        "CREATE INDEX IF NOT EXISTS idx_unit_year        ON finance(unit_id, year)"),
    ("idx_item_year",        "CREATE INDEX IF NOT EXISTS idx_item_year        ON finance(item_code, year)"),
    ("idx_year",             "CREATE INDEX IF NOT EXISTS idx_year             ON finance(year)"),
]

BATCH_SIZE = 10_000
COLUMN_COUNT = 14  # must match CREATE_TABLE_SQL and EXPORT_QUERY


# ---------------------------------------------------------------------------
# Step 1: Export PostgreSQL → CSV (streamed via COPY … TO STDOUT)
# ---------------------------------------------------------------------------

PSQL_SEARCH_PATHS = [
    r"C:\Program Files\PostgreSQL\17\bin\psql.exe",
    r"C:\Program Files\PostgreSQL\16\bin\psql.exe",
    r"C:\Program Files\PostgreSQL\15\bin\psql.exe",
    "psql",
]


def find_psql() -> str:
    import shutil
    for candidate in PSQL_SEARCH_PATHS:
        if shutil.which(candidate) or Path(candidate).exists():
            return candidate
    return "psql"


def export_postgres_to_csv(csv_path: Path, db_user: str, db_name: str, db_password: str) -> int:
    """Stream the enriched finance query out of PostgreSQL into a CSV file."""
    copy_sql = (
        f"COPY ({EXPORT_QUERY.strip()}) TO STDOUT WITH (FORMAT csv, HEADER true)"
    )

    env = os.environ.copy()
    env["PGPASSWORD"] = db_password

    psql = find_psql()
    print(f"  Using psql: {psql}")

    with csv_path.open("w", encoding="utf-8", newline="") as out_file:
        result = subprocess.run(
            [
                psql,
                "-U", db_user,
                "-d", db_name,
                "-v", "ON_ERROR_STOP=1",
                "-c", copy_sql,
            ],
            stdout=out_file,
            stderr=subprocess.PIPE,
            text=True,
            env=env,
        )

    if result.returncode != 0:
        print(f"  psql export failed:\n{result.stderr}", file=sys.stderr)
        return result.returncode

    size_mb = csv_path.stat().st_size / 1_048_576
    print(f"  PostgreSQL export complete  ({size_mb:.1f} MB)  →  {csv_path}")
    return 0


# ---------------------------------------------------------------------------
# Step 2: Build SQLite file from CSV
# ---------------------------------------------------------------------------

def build_sqlite(csv_path: Path, sqlite_path: Path) -> int:
    """Load CSV rows into a range-request-optimised SQLite database."""
    sqlite_path.parent.mkdir(parents=True, exist_ok=True)

    # Remove any existing file so PRAGMA page_size takes effect from scratch.
    if sqlite_path.exists():
        sqlite_path.unlink()
        print(f"  Removed existing file at {sqlite_path}")

    con = sqlite3.connect(str(sqlite_path))
    try:
        cur = con.cursor()

        # PRAGMAs that affect file layout must come before CREATE TABLE.
        cur.execute("PRAGMA page_size = 4096")
        # DELETE mode is required — WAL (.wal / .shm) sidecar files break
        # sql.js-httpvfs byte-range addressing.
        cur.execute("PRAGMA journal_mode = DELETE")
        # Speed up the bulk insert; safe because we rebuild from scratch.
        cur.execute("PRAGMA synchronous = OFF")
        cur.execute("PRAGMA cache_size = -65536")   # 64 MB in-memory cache

        cur.execute(CREATE_TABLE_SQL)
        con.commit()

        print("  Loading rows from CSV...")
        row_count = 0
        placeholder = f"({','.join(['?'] * COLUMN_COUNT)})"

        with csv_path.open("r", encoding="utf-8", newline="") as f:
            reader = csv.reader(f)
            next(reader)  # skip header

            batch: list[list[str]] = []
            for row in reader:
                batch.append(row)
                if len(batch) >= BATCH_SIZE:
                    cur.executemany(f"INSERT INTO finance VALUES {placeholder}", batch)
                    con.commit()
                    row_count += len(batch)
                    batch = []
                    print(f"    {row_count:,} rows...", end="\r", flush=True)

            if batch:
                cur.executemany(f"INSERT INTO finance VALUES {placeholder}", batch)
                con.commit()
                row_count += len(batch)

        print(f"\n  {row_count:,} rows loaded.")

        print("  Building indexes...")
        for name, stmt in INDEX_STATEMENTS:
            cur.execute(stmt)
            con.commit()
            print(f"    ✓ {name}")

        print("  Running VACUUM (defragments pages for optimal range reads)...")
        cur.execute("VACUUM")

        return row_count

    finally:
        con.close()


# ---------------------------------------------------------------------------
# Step 3: Validate SQLite output
# ---------------------------------------------------------------------------

def validate_sqlite(sqlite_path: Path) -> bool:
    """Run sanity checks on the finished SQLite file."""
    con = sqlite3.connect(str(sqlite_path))
    try:
        cur = con.cursor()

        checks = [
            ("Total rows",            "SELECT COUNT(*) FROM finance"),
            ("Distinct states",       "SELECT COUNT(DISTINCT state_fips) FROM finance"),
            ("Distinct governments",  "SELECT COUNT(DISTINCT unit_id) FROM finance"),
            ("Distinct item codes",   "SELECT COUNT(DISTINCT item_code) FROM finance"),
            ("Distinct years",        "SELECT COUNT(DISTINCT year) FROM finance"),
            ("Null unit_id rows",     "SELECT COUNT(*) FROM finance WHERE unit_id  = '' OR unit_id  IS NULL"),
            ("Null item_code rows",   "SELECT COUNT(*) FROM finance WHERE item_code = '' OR item_code IS NULL"),
            ("Zero amount rows",      "SELECT COUNT(*) FROM finance WHERE amount_thousands = 0"),
        ]

        print("\n  Validation checks:")
        all_pass = True
        for label, query in checks:
            (value,) = cur.execute(query).fetchone()
            is_null_check = label.startswith("Null")
            fail = is_null_check and value > 0
            status = "FAIL" if fail else "ok"
            print(f"    [{status}] {label}: {value:,}")
            if fail:
                all_pass = False

        # Spot-check: Washington (state_fips='53') must have data.
        (wa_rows,) = cur.execute(
            "SELECT COUNT(*) FROM finance WHERE state_fips = '53'"
        ).fetchone()
        if wa_rows == 0:
            print("    [FAIL] Washington state (53) has 0 rows — check source data.")
            all_pass = False
        else:
            print(f"    [ok]   Washington state (53) rows: {wa_rows:,}")

        # Spot-check: index existence via sqlite_master.
        (idx_count,) = cur.execute(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND tbl_name='finance'"
        ).fetchone()
        if idx_count < len(INDEX_STATEMENTS):
            print(f"    [FAIL] Expected {len(INDEX_STATEMENTS)} indexes, found {idx_count}.")
            all_pass = False
        else:
            print(f"    [ok]   {idx_count} indexes present.")

        # Spot-check: page_size.
        (page_size,) = cur.execute("PRAGMA page_size").fetchone()
        if page_size != 4096:
            print(f"    [FAIL] page_size = {page_size}, expected 4096.")
            all_pass = False
        else:
            print(f"    [ok]   page_size = {page_size}")

        return all_pass

    finally:
        con.close()


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser(
        description="Export GovLens finance data to SQLite for the Phase 7 choropleth map."
    )
    parser.add_argument("--db-user",     default=os.environ.get("DB_USER",    "postgres"))
    parser.add_argument("--db-name",     default=os.environ.get("DB_NAME",    "govlens"))
    parser.add_argument("--db-password", default=os.environ.get("PGPASSWORD", "postgres"))
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("data/output/govlens_map.sqlite3"),
        help="Output SQLite file path (default: data/output/govlens_map.sqlite3)",
    )
    parser.add_argument(
        "--workspace-root",
        type=Path,
        default=Path.cwd(),
    )
    args = parser.parse_args()

    sqlite_path = (
        args.output if args.output.is_absolute()
        else args.workspace_root / args.output
    )
    csv_path = sqlite_path.with_suffix(".export_tmp.csv")

    print(f"\n{'='*60}")
    print("GovLens SQLite Map Export — Phase 7.1")
    print(f"{'='*60}")
    print(f"Target : {sqlite_path}")
    print(f"DB     : {args.db_name}  (user: {args.db_user})")

    start = datetime.utcnow()

    try:
        print("\n[1/4] Exporting from PostgreSQL...")
        rc = export_postgres_to_csv(csv_path, args.db_user, args.db_name, args.db_password)
        if rc != 0:
            return 1

        print("\n[2/4] Building SQLite file...")
        row_count = build_sqlite(csv_path, sqlite_path)

        db_size_mb = sqlite_path.stat().st_size / 1_048_576
        print(f"  SQLite file size: {db_size_mb:.1f} MB")

        print("\n[3/4] Validating output...")
        passed = validate_sqlite(sqlite_path)

        elapsed = (datetime.utcnow() - start).total_seconds()
        print(f"\n[4/4] Finished in {elapsed:.1f}s")

        if passed:
            print(
                f"\n✓ Export complete: {sqlite_path.name}"
                f"  ({db_size_mb:.1f} MB, {row_count:,} rows)"
            )
            return 0
        else:
            print("\n✗ Validation failed — review errors above before deploying to S3.")
            return 1

    finally:
        if csv_path.exists():
            csv_path.unlink()


if __name__ == "__main__":
    sys.exit(main())
