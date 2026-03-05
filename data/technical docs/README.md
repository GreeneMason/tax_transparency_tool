# Technical Docs

This folder stores source reference documents used to interpret and validate Census finance data files.

## Documents

- **2023 S&L Public Use Files Technical Documentation.pdf**
  - Official U.S. Census technical documentation for 2023 state and local finance public-use files.
  - Defines record layouts for `2023FinEstDAT_06052025modp_pu.txt` and `Fin_PID_2023.txt`.
  - Provides legend definitions used by parser scripts:
    - State codes
    - Individual unit government type codes
    - Item code descriptions
    - Function codes for special districts
    - Imputation/data flags

## How this is used in GovLens

- `scripts/parse_tech_docs_legends.py` reads this PDF and generates structured legend CSV files in `data/legends/`.
- Those legend CSVs are then used by `scripts/parse_finestdat.py` to enrich raw finance records with human-readable labels.
