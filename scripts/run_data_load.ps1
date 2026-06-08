# GovLens Data Load Wrapper (PowerShell)
# Executes the orchestrated data load pipeline with automatic DB credential detection

param(
    [string]$WorkspaceRoot = (Get-Location).Path,
    [string]$DbUser = "postgres",
    [string]$DbName = "govlens",
    [string]$DbPassword = "postgres",
    [switch]$Help
)

if ($Help) {
    Write-Host @"
GovLens Data Load Wrapper

Usage:
  .\run_data_load.ps1 [options]

Options:
  -WorkspaceRoot <path>  Root directory of govlens workspace (default: current directory)
  -DbUser <name>         PostgreSQL user (default: postgres)
  -DbName <name>         PostgreSQL database (default: govlens)
  -DbPassword <pwd>      PostgreSQL password (default: postgres)
  -Help                  Show this help

Examples:
  # Default (assume localhost, postgres user, govlens db)
  .\run_data_load.ps1

  # Custom workspace and credentials
  .\run_data_load.ps1 -WorkspaceRoot C:\govlens -DbUser admin -DbPassword mypass

"@
    exit 0
}

Write-Host "=====================================================================" -ForegroundColor Green
Write-Host "GovLens Data Load Pipeline" -ForegroundColor Green
Write-Host "=====================================================================" -ForegroundColor Green
Write-Host "WorkspaceRoot: $WorkspaceRoot"
Write-Host "DbUser: $DbUser"
Write-Host "DbName: $DbName"
Write-Host ""

# Verify Python is available
$pythonCmd = Get-Command python3 -ErrorAction SilentlyContinue
if (-not $pythonCmd) {
    $pythonCmd = Get-Command python -ErrorAction SilentlyContinue
}
if (-not $pythonCmd) {
    Write-Error "Python not found on PATH. Please install Python 3.8+ or add to PATH." -ErrorAction Stop
}

Write-Host "Python: $($pythonCmd.Source)"
Write-Host ""

# Set environment for psql and run orchestrator
$env:PGPASSWORD = $DbPassword
$env:PATH = "C:\Program Files\PostgreSQL\17\bin;$env:PATH"

$scriptPath = Join-Path $WorkspaceRoot "scripts\run_full_data_load.py"
if (-not (Test-Path $scriptPath)) {
    Write-Error "Data load script not found: $scriptPath" -ErrorAction Stop
}

Write-Host "Starting data load pipeline..." -ForegroundColor Cyan
Write-Host ""

& python $scriptPath `
    --workspace-root $WorkspaceRoot `
    --db-user $DbUser `
    --db-name $DbName `
    --db-password $DbPassword

$exitCode = $LASTEXITCODE

Write-Host ""
Write-Host "=====================================================================" -ForegroundColor (if ($exitCode -eq 0) { "Green" } else { "Red" })
if ($exitCode -eq 0) {
    Write-Host "✓ Data load pipeline completed successfully" -ForegroundColor Green
} else {
    Write-Host "✗ Data load pipeline failed with exit code $exitCode" -ForegroundColor Red
}
Write-Host "=====================================================================" -ForegroundColor (if ($exitCode -eq 0) { "Green" } else { "Red" })

# Find and display the latest report
$reportDir = Join-Path $WorkspaceRoot "data\output"
$latestReport = Get-ChildItem -Path $reportDir -Filter "data_load_report_*.json" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1

if ($latestReport) {
    Write-Host ""
    Write-Host "Latest report: $($latestReport.FullName)" -ForegroundColor Cyan
    Write-Host ""
    $reportContent = Get-Content $latestReport.FullName | ConvertFrom-Json
    Write-Host "Summary:"
    Write-Host "  Status: $($reportContent.summary.status)"
    if ($reportContent.summary.errors -and $reportContent.summary.errors.Count -gt 0) {
        Write-Host "  Errors:"
        $reportContent.summary.errors | ForEach-Object { Write-Host "    - $_" }
    }
    if ($reportContent.statistics) {
        Write-Host "  Row counts:"
        $reportContent.statistics | ForEach-Object {
            $_.PSObject.Properties | ForEach-Object {
                Write-Host "    $($_.Name): $($_.Value)"
            }
        }
    }
}

exit $exitCode
