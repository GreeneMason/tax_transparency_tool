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
        "item_code": item_code,
        "amount": amount,
        "year": year,
        "record_type": record_type,
    }


def parse_pid_line(line: str) -> tuple[str, str]:
    record = line.rstrip("\r\n")
    if not record:
        return "", ""

    if len(record) < 12:
        return "", ""

    if len(record) < PID_WIDTH:
        record = record.ljust(PID_WIDTH)

    unit_id = record[0:12].strip()
    name_block = record[12:111].rstrip()
    primary_name = " ".join(name_block.split())

    return unit_id, primary_name


def load_pid_names(pid_path: Path) -> dict[str, str]:
    names: dict[str, str] = {}
    with pid_path.open("r", encoding="utf-8", errors="replace") as handle:
        for line in handle:
            unit_id, name = parse_pid_line(line)
            if unit_id and name:
                names[unit_id] = name
    return names


def parse_finestdat_file(fin_path: Path, pid_names: dict[str, str] | None = None) -> list[dict[str, str | int]]:
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

            if pid_names is not None:
                row["unit_name"] = pid_names.get(str(row["unit_id"]), "")

            rows.append(row)
    return rows


def write_csv(rows: list[dict[str, str | int]], output_path: Path, include_unit_name: bool) -> None:
    fieldnames = ["unit_id", "item_code", "amount", "year", "record_type"]
    if include_unit_name:
        fieldnames.append("unit_name")

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
        "--output",
        default="data/output/finestdat_2023_labeled.csv",
        help="Output CSV path.",
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
    output_path = Path(args.output)

    pid_names: dict[str, str] | None = None
    include_unit_name = False

    if not args.skip_pid:
        if not pid_path.exists():
            raise FileNotFoundError(f"PID file not found: {pid_path}")
        pid_names = load_pid_names(pid_path)
        include_unit_name = True

    rows = parse_finestdat_file(fin_path, pid_names=pid_names)
    write_csv(rows, output_path, include_unit_name=include_unit_name)

    print(f"Parsed {len(rows):,} records -> {output_path}")


if __name__ == "__main__":
    main()
