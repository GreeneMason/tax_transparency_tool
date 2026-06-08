# API Contract Snapshot Tests

This directory contains API contract snapshots used for versioned API testing.

## Purpose
Snapshots validate that API response structure remains stable across deployments. Changes to response schemas must be explicitly reviewed and approved.

## Format
Each endpoint has a `.json` snapshot file documenting the expected response structure and data types.

## Testing
Integration tests in `ApiContractIT.java` verify responses match expected structure:
- Required fields are present
- Data types are correct
- Pagination metadata is included
- Error responses follow consistent schema

## Maintenance
If an API response structure legitimately changes:
1. Update the relevant snapshot file
2. Review the change in code review (ensures intent)
3. Document the breaking change in release notes
4. Update API documentation

## Snapshots

### Government Search
- File: `government_search_response.json`
- Endpoint: `GET /api/v1/governments`
- Validates: pagination, government fields, sorting order

### ZIP Lookup
- File: `zip_lookup_response.json`
- Endpoint: `GET /api/v1/governments/by-zip`
- Validates: pagination, ZIP-matched governments, HUD ratio presence

### Expense Breakdown
- File: `expense_breakdown_response.json`
- Endpoint: `GET /api/v1/governments/{unitId}/expense-breakdown`
- Validates: category structure, amount fields, aggregation correctness

### Comparison
- File: `comparison_response.json`
- Endpoint: `GET /api/v1/compare`
- Validates: side-by-side item structure, difference calculations
