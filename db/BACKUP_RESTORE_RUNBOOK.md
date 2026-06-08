# GovLens Backup and Restore Runbook

## Purpose
Define minimum backup and recovery steps for the production Postgres database.

## Targets
- RPO: <= 24 hours (at most one day of data loss)
- RTO: <= 2 hours (restore service in two hours)

## Daily Backup Procedure
1. Run a compressed logical backup:
   ```bash
   pg_dump -Fc -d <database_name> -f backups/govlens_YYYYMMDD.dump
   ```
2. Validate the file exists and is non-zero:
   ```bash
   ls -lh backups/govlens_YYYYMMDD.dump
   ```
3. Upload backup to off-host storage (cloud bucket or secure file store).
4. Record backup metadata (timestamp, size, checksum, operator) in release notes.

## Weekly Restore Test (Required)
1. Create or reset a disposable restore database:
   ```bash
   createdb govlens_restore_test
   ```
2. Restore latest backup:
   ```bash
   pg_restore -d govlens_restore_test backups/govlens_YYYYMMDD.dump
   ```
3. Run validation checks:
   ```bash
   psql -d govlens_restore_test -f db/validate_wa_load.sql
   psql -d govlens_restore_test -f db/validate_release_gate.sql
   ```
4. Record pass/fail results and remediation notes.

## Incident Restore Procedure
1. Put application in maintenance mode.
2. Identify the latest known-good backup.
3. Restore to a clean database target:
   ```bash
   dropdb --if-exists govlens_prod_recovery
   createdb govlens_prod_recovery
   pg_restore -d govlens_prod_recovery backups/govlens_YYYYMMDD.dump
   ```
4. Run smoke validation:
   ```bash
   psql -d govlens_prod_recovery -f db/validate_release_gate.sql
   ```
5. Point application to recovered database and verify `/health` is UP.
6. Exit maintenance mode.

## Rollback Trigger Conditions
- Migration failure during deploy.
- Release gate SQL fails in staging or production.
- Critical endpoint error rate breaches SLO after deploy.

## Ownership
- Primary owner: backend/data maintainer on release duty.
- Secondary owner: on-call engineer.
