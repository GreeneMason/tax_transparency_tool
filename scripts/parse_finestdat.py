from __future__ import annotations

import argparse
import csv
from pathlib import Path


FINESTDAT_WIDTH = 32
PID_WIDTH = 146


def parse_finestdat_line(line: str) -> dict[str, str | int]:
    record = line.rstrip("\r\n")
    if len(record) < FINESTDAT_WIDTH:
        raise ValueError(f"Expected at least {FINESTDAT_WIDTH} characters, got {len(record)}")

    unit_id = record[0:12].strip()
    item_code = record[12:15].strip()
    amount_raw = record[15:27].strip()
    year_raw = record[27:31].strip()
    record_type = record[31:32].strip()

    amount = int(amount_raw) if amount_raw else 0
    year = int(year_raw) if year_raw else 0

    return {
        "unit_id": unit_id,
        "state_fips": unit_id[0:2],
        "gov_type_code": unit_id[2:3],
        "county_fips": unit_id[3:6],
        "unit_identifier": unit_id[6:12],
        "item_code": item_code,
        "amount": amount,
        "year": year,
        "imputation_flag": record_type,
    }


def parse_pid_line(line: str) -> dict[str, str]:
    record = line.rstrip("\r\n")
    if not record:
        return {}

    if len(record) < 12:
        return {}

    if len(record) < PID_WIDTH:
        record = record.ljust(PID_WIDTH)

    def normalized(segment: str) -> str:
        return " ".join(segment.strip().split())

    return {
        "unit_id": record[0:12].strip(),
        "id_name": normalized(record[12:76]),
        "county_name": normalized(record[76:111]),
        "place_fips": record[111:116].strip(),
        "population": record[116:125].strip(),
        "population_year": record[125:127].strip(),
        "enrollment": record[127:134].strip(),
        "enrollment_year": record[134:136].strip(),
        "function_code": record[136:138].strip(),
        "school_level_code": record[138:140].strip(),
        "fiscal_year_ending": record[140:144].strip(),
        "survey_year": record[144:146].strip(),
    }


def load_pid_index(pid_path: Path) -> dict[str, dict[str, str]]:
    pid_index: dict[str, dict[str, str]] = {}
    with pid_path.open("r", encoding="utf-8", errors="replace") as handle:
        for line in handle:
            row = parse_pid_line(line)
            unit_id = row.get("unit_id", "")
            if unit_id:
                pid_index[unit_id] = row
    return pid_index


def load_legend_map(csv_path: Path, key_field: str, value_field: str) -> dict[str, str]:
    mapping: dict[str, str] = {}
    if not csv_path.exists():
        return mapping

    with csv_path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            key = (row.get(key_field) or "").strip()
            value = (row.get(value_field) or "").strip()
            if key:
                mapping[key] = value
    return mapping


def load_state_lookup(csv_path: Path) -> dict[str, dict[str, str]]:
    mapping: dict[str, dict[str, str]] = {}
    if not csv_path.exists():
        return mapping

    with csv_path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            fips = (row.get("fips") or "").strip()
            if fips:
                mapping[fips] = {
                    "state_name": (row.get("state_name") or "").strip(),
                    "state_abbrev": (row.get("abbrev") or "").strip(),
                }
    return mapping


def parse_finestdat_file(
    fin_path: Path,
    pid_index: dict[str, dict[str, str]] | None = None,
    item_code_map: dict[str, str] | None = None,
    imputation_flag_map: dict[str, str] | None = None,
    gov_type_map: dict[str, str] | None = None,
    state_map: dict[str, dict[str, str]] | None = None,
    state_fips_filter: str | None = None,
) -> list[dict[str, str | int]]:
    rows: list[dict[str, str | int]] = []
    with fin_path.open("r", encoding="utf-8", errors="replace") as handle:
        for line_number, line in enumerate(handle, start=1):
            stripped = line.rstrip("\r\n")
            if not stripped:
                continue

            try:
                row = parse_finestdat_line(stripped)
            except ValueError as exc:
                raise ValueError(f"Line {line_number}: {exc}") from exc

            state_fips = str(row["state_fips"])
            if state_fips_filter and state_fips != state_fips_filter:
                continue

            if item_code_map is not None:
                row["item_description"] = item_code_map.get(str(row["item_code"]), "")

            if imputation_flag_map is not None:
                row["imputation_flag_description"] = imputation_flag_map.get(str(row["imputation_flag"]), "")

            if gov_type_map is not None:
                row["gov_type_description"] = gov_type_map.get(str(row["gov_type_code"]), "")

            if state_map is not None:
                state_info = state_map.get(state_fips, {})
                row["state_name"] = state_info.get("state_name", "")
                row["state_abbrev"] = state_info.get("state_abbrev", "")

            if pid_index is not None:
                pid = pid_index.get(str(row["unit_id"]), {})
                row["unit_name"] = pid.get("id_name", "")
                row["county_name"] = pid.get("county_name", "")
                row["place_fips"] = pid.get("place_fips", "")
                row["population"] = pid.get("population", "")
                row["function_code"] = pid.get("function_code", "")

            rows.append(row)
    return rows


def write_csv(rows: list[dict[str, str | int]], output_path: Path, include_pid_fields: bool) -> None:
    fieldnames = [
        "unit_id",
        "state_fips",
        "state_abbrev",
        "state_name",
        "gov_type_code",
        "gov_type_description",
        "county_fips",
        "unit_identifier",
        "item_code",
        "item_description",
        "amount",
        "year",
        "imputation_flag",
        "imputation_flag_description",
    ]
    if include_pid_fields:
        fieldnames.extend(["unit_name", "county_name", "place_fips", "population", "function_code"])

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Parse Census 2023FinEstDAT fixed-width records into a labeled CSV table."
    )
    parser.add_argument(
        "--finestdat",
        default="data/2023FinEstDAT_06052025modp_pu.txt",
        help="Path to the 2023FinEstDAT fixed-width file.",
    )
    parser.add_argument(
        "--pid",
        default="data/Fin_PID_2023.txt",
        help="Path to the Fin_PID_2023 fixed-width file for Unit ID to name mapping.",
    )
    parser.add_argument(
        "--legend-dir",
        default="data/legends",
        help="Directory containing legend CSV files produced from the technical documentation.",
    )
    parser.add_argument(
        "--output",
        default="data/output/finestdat_2023_wa_enriched.csv",
        help="Output CSV path.",
    )
    parser.add_argument(
        "--state-fips",
        default="53",
        help="Filter output to a specific 2-digit state FIPS code. Use empty string for all states.",
    )
    parser.add_argument(
        "--skip-pid",
        action="store_true",
        help="Skip PID enrichment and output only FinEstDAT columns.",
    )
    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()

    fin_path = Path(args.finestdat)
    pid_path = Path(args.pid)
    legend_dir = Path(args.legend_dir)
    output_path = Path(args.output)
    state_fips_filter = args.state_fips.strip() or None

    pid_index: dict[str, dict[str, str]] | None = None
    include_pid_fields = False

    if not args.skip_pid:
        if not pid_path.exists():
            raise FileNotFoundError(f"PID file not found: {pid_path}")
        pid_index = load_pid_index(pid_path)
        include_pid_fields = True

    item_code_map = load_legend_map(legend_dir / "legend_item_codes.csv", "item_code", "description")
    imputation_flag_map = load_legend_map(legend_dir / "legend_imputation_flags.csv", "flag", "description")
    gov_type_map = load_legend_map(legend_dir / "legend_gov_type_codes.csv", "type_code", "description")
    state_map = load_state_lookup(legend_dir / "legend_state_codes.csv")

    rows = parse_finestdat_file(
        fin_path,
        pid_index=pid_index,
        item_code_map=item_code_map,
        imputation_flag_map=imputation_flag_map,
        gov_type_map=gov_type_map,
        state_map=state_map,
        state_fips_filter=state_fips_filter,
    )
    write_csv(rows, output_path, include_pid_fields=include_pid_fields)

    scope = state_fips_filter if state_fips_filter else "ALL"
    print(f"Parsed {len(rows):,} records (state_fips={scope}) -> {output_path}")


if __name__ == "__main__":
    main()
