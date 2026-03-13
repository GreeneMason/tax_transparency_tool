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

## Endpoint 2: Compare Two Governments

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
