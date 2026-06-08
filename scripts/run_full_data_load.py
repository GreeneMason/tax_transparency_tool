#!/usr/bin/env python3
"""
GovLens Data Load Orchestrator

Runs the complete ETL pipeline:
1. Parse FinEstDAT fixed-width file to CSV
2. Load staging table via psql \copy
3. Promote staging to dimensions and facts
4. Run release-gate validation checks
5. Generate structured load report (JSON)

Exit code:
  0 = success
  1 = validation or execution failure
"""

import argparse
import json
import subprocess
import sys
from pathlib import Path
from datetime import datetime
from typing import Dict, List, Any


class DataLoadOrchestrator:
    def __init__(self, workspace_root: Path, env_config: Dict[str, str]):
        self.workspace_root = workspace_root
        self.env_config = env_config
        self.report: Dict[str, Any] = {
            "timestamp": datetime.utcnow().isoformat(),
            "workspace_root": str(workspace_root),
            "steps": [],
            "summary": {"status": "in_progress", "errors": []},
        }

    def run_step(self, name: str, command: List[str], description: str) -> bool:
        """Execute a step and record results."""
        print(f"\n{'='*70}")
        print(f"STEP: {name}")
        print(f"Description: {description}")
        print(f"Command: {' '.join(command)}")
        print(f"{'='*70}")

        start_time = datetime.utcnow()
        step_result = {
            "name": name,
            "description": description,
            "start_time": start_time.isoformat(),
            "status": "pending",
            "error": None,
        }

        try:
            result = subprocess.run(
                command,
                cwd=self.workspace_root,
                capture_output=True,
                text=True,
                timeout=600,
                env={**self.env_config},
            )

            if result.returncode == 0:
                step_result["status"] = "success"
                print(f"✓ {name} completed successfully.")
                if result.stdout:
                    print(f"Output:\n{result.stdout}")
            else:
                step_result["status"] = "failure"
                step_result["error"] = result.stderr or result.stdout or "Unknown error"
                print(f"✗ {name} failed with exit code {result.returncode}")
                print(f"Error:\n{step_result['error']}")
                self.report["summary"]["errors"].append(
                    f"{name}: {step_result['error'][:200]}"
                )
                return False

        except subprocess.TimeoutExpired:
            step_result["status"] = "timeout"
            step_result["error"] = f"Command timed out after 600 seconds"
            print(f"✗ {name} timed out")
            self.report["summary"]["errors"].append(f"{name}: timeout")
            return False

        except Exception as e:
            step_result["status"] = "error"
            step_result["error"] = str(e)
            print(f"✗ {name} raised exception: {e}")
            self.report["summary"]["errors"].append(f"{name}: {str(e)[:200]}")
            return False

        step_result["end_time"] = datetime.utcnow().isoformat()
        self.report["steps"].append(step_result)
        return True

    def run_parse(self) -> bool:
        """Step 1: Parse FinEstDAT file to CSV."""
        cmd = [
            sys.executable,
            "scripts/parse_finestdat.py",
            "--finestdat-path",
            "data/2023FinEstDAT_06052025modp_pu.txt",
            "--pid-path",
            "data/Fin_PID_2023.txt",
            "--legends-dir",
            "data/legends",
            "--output-csv",
            "data/output/finestdat_2023_us_enriched.csv",
            "--include-pid-fields",
        ]
        return self.run_step(
            "parse_finestdat",
            cmd,
            "Parse Census 2023FinEstDAT fixed-width file and enrich with PID/legend mappings",
        )

    def run_stage_load(self) -> bool:
        """Step 2: Load CSV into staging table via psql."""
        psql_cmd = f"""
        TRUNCATE TABLE govlens.stg_finance_unit_item CASCADE;
        \\copy govlens.stg_finance_unit_item(
          unit_id,state_fips,state_abbrev,state_name,gov_type_code,gov_type_description,
          county_fips,unit_identifier,item_code,item_description,amount,year,
          imputation_flag,imputation_flag_description,unit_name,county_name,place_fips,
          population,function_code
        )
        FROM '{self.workspace_root}/data/output/finestdat_2023_us_enriched.csv'
        WITH (FORMAT csv, HEADER true);
        """

        cmd = [
            "psql",
            "-U",
            self.env_config.get("DB_USER", "postgres"),
            "-d",
            self.env_config.get("DB_NAME", "govlens"),
            "-v",
            "ON_ERROR_STOP=1",
            "-c",
            psql_cmd,
        ]
        return self.run_step(
            "stage_load",
            cmd,
            "Load parsed CSV into staging table",
        )

    def run_promote_facts(self) -> bool:
        """Step 3: Promote staging to dimensions and facts."""
        cmd = [
            "psql",
            "-U",
            self.env_config.get("DB_USER", "postgres"),
            "-d",
            self.env_config.get("DB_NAME", "govlens"),
            "-v",
            "ON_ERROR_STOP=1",
            "-f",
            "db/load_finance.sql",
        ]
        return self.run_step(
            "promote_facts",
            cmd,
            "Promote staging data to dimensions (dim_*) and fact (fact_finance_unit_item_year)",
        )

    def run_release_gate(self) -> bool:
        """Step 4: Run release-gate validation checks."""
        cmd = [
            "psql",
            "-U",
            self.env_config.get("DB_USER", "postgres"),
            "-d",
            self.env_config.get("DB_NAME", "govlens"),
            "-v",
            "ON_ERROR_STOP=1",
            "-f",
            "db/validate_release_gate.sql",
        ]
        return self.run_step(
            "release_gate",
            cmd,
            "Enforce data-quality gates (referential integrity, duplicates, null checks)",
        )

    def collect_stats(self) -> None:
        """Collect row-count statistics for the report."""
        cmd = [
            "psql",
            "-U",
            self.env_config.get("DB_USER", "postgres"),
            "-d",
            self.env_config.get("DB_NAME", "govlens"),
            "-t",
            "-c",
            """
            SELECT json_build_object(
              'staging_rows', (SELECT COUNT(*) FROM govlens.stg_finance_unit_item),
              'dim_states', (SELECT COUNT(*) FROM govlens.dim_state),
              'dim_governments', (SELECT COUNT(*) FROM govlens.dim_government_unit),
              'dim_item_codes', (SELECT COUNT(*) FROM govlens.dim_item_code),
              'fact_rows', (SELECT COUNT(*) FROM govlens.fact_finance_unit_item_year)
            )::text;
            """,
        ]

        try:
            result = subprocess.run(
                cmd,
                cwd=self.workspace_root,
                capture_output=True,
                text=True,
                timeout=60,
                env={**self.env_config},
            )
            if result.returncode == 0 and result.stdout.strip():
                self.report["statistics"] = json.loads(result.stdout.strip())
        except Exception as e:
            print(f"Warning: Could not collect statistics: {e}")

    def save_report(self, output_path: Path) -> None:
        """Save the report to JSON."""
        output_path.parent.mkdir(parents=True, exist_ok=True)
        with output_path.open("w") as f:
            json.dump(self.report, f, indent=2)
        print(f"\n✓ Report saved to {output_path}")

    def run(self) -> int:
        """Execute the full pipeline."""
        try:
            if not self.run_parse():
                self.report["summary"]["status"] = "failed"
                return 1

            if not self.run_stage_load():
                self.report["summary"]["status"] = "failed"
                return 1

            if not self.run_promote_facts():
                self.report["summary"]["status"] = "failed"
                return 1

            if not self.run_release_gate():
                self.report["summary"]["status"] = "failed"
                return 1

            self.collect_stats()
            self.report["summary"]["status"] = "success"
            print("\n" + "=" * 70)
            print("✓ All steps completed successfully!")
            print("=" * 70)
            return 0

        except Exception as e:
            self.report["summary"]["status"] = "error"
            self.report["summary"]["errors"].append(f"Unexpected error: {str(e)}")
            print(f"\n✗ Orchestrator error: {e}")
            return 1

        finally:
            # Always save report
            output_path = (
                self.workspace_root
                / "data"
                / "output"
                / f"data_load_report_{datetime.utcnow().strftime('%Y%m%d_%H%M%S')}.json"
            )
            self.save_report(output_path)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Orchestrate GovLens data load pipeline with validation."
    )
    parser.add_argument(
        "--workspace-root",
        type=Path,
        default=Path.cwd(),
        help="Root directory of the govlens workspace (default: cwd)",
    )
    parser.add_argument(
        "--db-user",
        default="postgres",
        help="PostgreSQL user (default: postgres, override via env: DB_USER)",
    )
    parser.add_argument(
        "--db-name",
        default="govlens",
        help="PostgreSQL database (default: govlens, override via env: DB_NAME)",
    )
    parser.add_argument(
        "--db-password",
        default="postgres",
        help="PostgreSQL password (default: postgres, override via env: DB_PASSWORD)",
    )
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    env_config = {
        "DB_USER": args.db_user,
        "DB_NAME": args.db_name,
        "PGPASSWORD": args.db_password,
        "PATH": f"C:\\Program Files\\PostgreSQL\\17\\bin;{Path.home()}\\AppData\\Local\\Programs\\Python\\Python311\\Scripts",
    }

    orchestrator = DataLoadOrchestrator(args.workspace_root, env_config)
    return orchestrator.run()


if __name__ == "__main__":
    sys.exit(main())
