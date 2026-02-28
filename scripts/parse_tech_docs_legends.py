from __future__ import annotations

import argparse
import csv
import re
from pathlib import Path

from pypdf import PdfReader


def extract_pdf_text(pdf_path: Path) -> str:
    reader = PdfReader(str(pdf_path))
    pages = [(page.extract_text() or "") for page in reader.pages]
    return "\n".join(pages)


def clean_lines(text: str) -> list[str]:
    lines = []
    for raw in text.splitlines():
        line = " ".join(raw.replace("\t", " ").split())
        if line:
            lines.append(line)
    return lines


def slice_section(lines: list[str], start_marker: str, end_marker: str | None) -> list[str]:
    start_index = next((idx for idx, line in enumerate(lines) if start_marker in line), None)
    if start_index is None:
        raise ValueError(f"Start marker not found: {start_marker}")

    search_start = start_index + 1
    if end_marker is None:
        end_index = len(lines)
    else:
        end_index = next((idx for idx, line in enumerate(lines[search_start:], start=search_start) if end_marker in line), None)
        if end_index is None:
            raise ValueError(f"End marker not found for section starting '{start_marker}': {end_marker}")

    return lines[search_start:end_index]


def parse_state_codes(lines: list[str]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    pattern = re.compile(r"^(?P<state_name>.+?)\s+(?P<abbrev>[A-Z]{2})\s+(?P<fips>\d{2})$")

    for line in lines:
        if line.startswith("State Abbreviations") or line.startswith("State Name Abbreviation"):
            continue
        match = pattern.match(line)
        if match:
            rows.append(match.groupdict())

    return rows


def parse_gov_type_codes(lines: list[str]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    pattern = re.compile(r"^(?P<type_code>\d)\s+(?P<description>.+)$")

    for line in lines:
        if line.startswith("Type Code Description"):
            continue
        match = pattern.match(line)
        if match:
            rows.append(match.groupdict())

    return rows


def parse_item_codes(lines: list[str]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    code_pattern = re.compile(r"^(?P<item_code>[0-9A-Z]{3})\s+(?P<description>.+)$")
    current: dict[str, str] | None = None

    for line in lines:
        if line.startswith("Item Code Description"):
            continue

        match = code_pattern.match(line)
        if match:
            current = match.groupdict()
            rows.append(current)
        elif current is not None:
            current["description"] = f"{current['description']} {line}".strip()

    return rows


def parse_function_codes(lines: list[str]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    pattern = re.compile(r"^(?P<function_code>\d{2})\s+(?P<description>.+)$")
    current: dict[str, str] | None = None

    for line in lines:
        if line.startswith("Indicates type of service"):
            continue
        match = pattern.match(line)
        if match:
            current = match.groupdict()
            rows.append(current)
        elif current is not None:
            current["description"] = f"{current['description']} {line}".strip()

    return rows


def parse_imputation_flags(lines: list[str]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    pattern = re.compile(r"^(?P<flag>[A-Z])\s+(?P<description>.+)$")

    for line in lines:
        if line.startswith("Flags Description"):
            continue
        match = pattern.match(line)
        if match:
            rows.append(match.groupdict())

    return rows


def write_csv(path: Path, fieldnames: list[str], rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Parse legend sections from Census technical documentation PDF into separate CSV files."
    )
    parser.add_argument(
        "--pdf",
        default="data/technical docs/2023 S&L Public Use Files Technical Documentation.pdf",
        help="Path to the technical documentation PDF.",
    )
    parser.add_argument(
        "--out-dir",
        default="data/legends",
        help="Directory where legend CSV files will be written.",
    )
    args = parser.parse_args()

    pdf_path = Path(args.pdf)
    out_dir = Path(args.out_dir)

    if not pdf_path.exists():
        raise FileNotFoundError(f"PDF not found: {pdf_path}")

    text = extract_pdf_text(pdf_path)
    lines = clean_lines(text)

    state_lines = slice_section(lines, "State Code Definitions", "Individual Unit – Type of Government Codes")
    gov_type_lines = slice_section(lines, "Individual Unit – Type of Government Codes", "Below are item codes and their titles")
    item_lines = slice_section(lines, "Item Codes and Short Descriptions", "Function Code for Special Districts")
    function_lines = slice_section(lines, "Function Code for Special Districts", "Imputation Type/Item Data Flags")
    imputation_lines = slice_section(lines, "Imputation Type/Item Data Flags", "Sources and Contacts")

    state_rows = parse_state_codes(state_lines)
    gov_type_rows = parse_gov_type_codes(gov_type_lines)
    item_rows = parse_item_codes(item_lines)
    function_rows = parse_function_codes(function_lines)
    imputation_rows = parse_imputation_flags(imputation_lines)

    write_csv(out_dir / "legend_state_codes.csv", ["state_name", "abbrev", "fips"], state_rows)
    write_csv(out_dir / "legend_gov_type_codes.csv", ["type_code", "description"], gov_type_rows)
    write_csv(out_dir / "legend_item_codes.csv", ["item_code", "description"], item_rows)
    write_csv(out_dir / "legend_function_codes.csv", ["function_code", "description"], function_rows)
    write_csv(out_dir / "legend_imputation_flags.csv", ["flag", "description"], imputation_rows)

    print(f"Wrote {len(state_rows)} state codes -> {out_dir / 'legend_state_codes.csv'}")
    print(f"Wrote {len(gov_type_rows)} gov type codes -> {out_dir / 'legend_gov_type_codes.csv'}")
    print(f"Wrote {len(item_rows)} item codes -> {out_dir / 'legend_item_codes.csv'}")
    print(f"Wrote {len(function_rows)} function codes -> {out_dir / 'legend_function_codes.csv'}")
    print(f"Wrote {len(imputation_rows)} imputation flags -> {out_dir / 'legend_imputation_flags.csv'}")


if __name__ == "__main__":
    main()
