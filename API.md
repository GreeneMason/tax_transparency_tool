# GovLens API (Washington MVP)

This document describes the current backend endpoints for the Washington-only MVP.

## Base URL

- Local: `http://localhost:8080`

## Run the API

1. Ensure PostgreSQL is running and the `govlens` database is loaded.
2. Start the API:

```bash
C:/Users/Smokable/tools/apache-maven-3.9.9/bin/mvn.cmd spring-boot:run
```

## Configuration

Environment variables (optional, defaults shown):

- `GOVLENS_DB_URL` = `jdbc:postgresql://localhost:5432/govlens`
- `GOVLENS_DB_USER` = `postgres`
- `GOVLENS_DB_PASSWORD` = `postgres`
- `PORT` = `8080`

## Endpoint 0: Health Check

### Request

- Method: `GET`
- Path: `/health`

### Example

```bash
curl "http://localhost:8080/health"
```

### Response (200)

```json
{
  "status": "UP",
  "database": "UP",
  "timestamp": "2026-03-12T18:20:00.000Z"
}
```

### Response (503)

```json
{
  "status": "DEGRADED",
  "database": "DOWN",
  "timestamp": "2026-03-12T18:20:00.000Z"
}
```

## Endpoint 1: Search Governments

### Request

- Method: `GET`
- Path: `/api/v1/governments`
- Query params:
  - `query` (required, minimum 2 chars)
  - `limit` (optional, default 25, max 100)

### Example

```bash
curl "http://localhost:8080/api/v1/governments?query=Sea&limit=3"
```

### Response (200)

```json
[
  {
    "unitId": "534033158973",
    "unitName": "PORT OF SEATTLE",
    "countyName": "King",
    "stateAbbrev": "WA",
    "stateName": "Washington",
    "govTypeCode": "4",
    "govTypeDescription": "Special District",
    "population": null
  },
  {
    "unitId": "532033184255",
    "unitName": "SEATTLE CITY",
    "countyName": "King",
    "stateAbbrev": "WA",
    "stateName": "Washington",
    "govTypeCode": "2",
    "govTypeDescription": "City",
    "population": 733919
  }
]
```

### Error (400)

```json
{
  "error": "Query must be at least 2 characters."
}
```

## Endpoint 2: Lookup Governments by ZIP

### Request

- Method: `GET`
- Path: `/api/v1/governments/by-zip`
- Query params:
  - `zip` (required, 5 digits)
  - `limit` (optional, default 25, max 100)

### Example

```bash
curl "http://localhost:8080/api/v1/governments/by-zip?zip=98101&limit=5"
```

### Response (200)

```json
[
  {
    "unitId": "532033184255",
    "unitName": "SEATTLE CITY",
    "countyName": "King",
    "stateAbbrev": "WA",
    "stateName": "Washington",
    "govTypeCode": "2",
    "govTypeDescription": "City",
    "population": 733919
  }
]
```

### Error (400)

```json
{
  "error": "zip must be a valid 5-digit ZIP code."
}
```

> Note: This endpoint requires `govlens.zip_to_unit_lookup` to be populated from HUD crosswalk-derived data.

## Endpoint 3: Income Tax Status (T09)

### Request

- Method: `GET`
- Path: `/api/v1/governments/{unitId}/income-tax-status`
- Query params:
  - `year` (optional, default 2023)

### Example

```bash
curl "http://localhost:8080/api/v1/governments/532033184255/income-tax-status?year=2023"
```

### Response (200)

```json
{
  "unitId": "532033184255",
  "year": 2023,
  "hasIncomeTax": false
}
```

### Error (400)

```json
{
  "error": "unitId must be exactly 12 characters."
}
```

> Logic: A government is marked `hasIncomeTax=true` when it has item code `T09` (Individual Income Tax) with amount greater than 0 for the selected year.

## Endpoint 4: Expense Breakdown (Pie Chart Data)

### Request

- Method: `GET`
- Path: `/api/v1/governments/{unitId}/expense-breakdown`
- Query params:
  - `year` (required)

### Example

```bash
curl "http://localhost:8080/api/v1/governments/532033184255/expense-breakdown?year=2023"
```

### Response (200)

```json
{
  "year": 2023,
  "government": {
    "unitId": "532033184255",
    "unitName": "SEATTLE CITY",
    "countyName": "King",
    "govTypeDescription": "City",
    "population": 733919
  },
  "totalExpensesThousands": 5366948,
  "categories": [
    {
      "category": "Current Operations",
      "amountThousands": 4664452,
      "percentage": 86.91,
      "aggregatedBucket": true,
      "items": [
        {
          "itemCode": "E44",
          "itemDescription": "Regular Highways-Current Oper",
          "amountThousands": 120000,
          "percentageWithinCategory": 2.57
        }
      ]
    },
    {
      "category": "Construction",
      "amountThousands": 417241,
      "percentage": 7.77,
      "aggregatedBucket": true,
      "items": []
    }
  ]
}
```

Each aggregated category may include an `items` array so clients can expand and view underlying Census item codes within that bucket.

### Error (400/404)

```json
{
  "error": "unitId must be exactly 12 characters."
}
```

## Endpoint 5: Compare Two Governments

### Request

- Method: `GET`
- Path: `/api/v1/compare`
- Query params:
  - `leftUnitId` (required, 12 chars)
  - `rightUnitId` (required, 12 chars)
  - `year` (required)

### Example

```bash
curl "http://localhost:8080/api/v1/compare?leftUnitId=532033184255&rightUnitId=532033176842&year=2023"
```

### Response (200)

```json
{
  "year": 2023,
  "leftGovernment": {
    "unitId": "532033184255",
    "unitName": "SEATTLE CITY",
    "countyName": "King",
    "govTypeDescription": "City",
    "population": 733919
  },
  "rightGovernment": {
    "unitId": "532033176842",
    "unitName": "SEATAC CITY",
    "countyName": "King",
    "govTypeDescription": "City",
    "population": 30759
  },
  "items": [
    {
      "itemCode": "49U",
      "itemDescription": "LTD Debt O/S-Unsp-Other NEC",
      "leftAmountThousands": 5749970,
      "rightAmountThousands": 6405,
      "differenceThousands": 5743565
    }
  ]
}
```

### Error (400)

```json
{
  "error": "leftUnitId and rightUnitId must be different."
}
```
