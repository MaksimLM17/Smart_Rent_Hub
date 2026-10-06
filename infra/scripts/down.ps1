<#
.SYNOPSIS
    Stops Smart Rent Hub local infrastructure containers.
.DESCRIPTION
    Shuts down running containers across core and obs profiles and frees allocated resources.
.PARAMETER Volumes
    Removes named data volumes (wipes PostgreSQL, Redis, MinIO, Kafka, Prometheus, Loki data).
.PARAMETER Profiles
    List of profiles to tear down. Defaults to @('core', 'obs') to clean up all services.
.EXAMPLE
    .\infra\scripts\down.ps1
    .\infra\scripts\down.ps1 -Volumes
#>

[CmdletBinding()]
param(
    [string[]]$Profiles = @("core", "obs"),
    [switch]$Volumes,
    [switch]$RemoveOrphans = $true
)

$ErrorActionPreference = "Stop"

# Resolve root repository directory
$rootDir = (Resolve-Path "$PSScriptRoot/../..").Path

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " [Smart Rent Hub] Stopping Local Infrastructure             " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Docker CLI not found." -ForegroundColor Red
    exit 1
}

$prevEAP = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
$null = docker info 2>&1
$isDockerRunning = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = $prevEAP

if (-not $isDockerRunning) {
    Write-Host "[WARNING] Docker daemon is not running. Nothing to stop." -ForegroundColor Yellow
    exit 0
}

$composeFile = Join-Path $rootDir "docker-compose.yml"
$composeArgs = @("-f", $composeFile)

foreach ($p in $Profiles) {
    $composeArgs += @("--profile", $p.ToLower())
}

$composeArgs += "down"

if ($Volumes) {
    Write-Host "[WARNING] Volumes flag specified: all persistent data volumes will be deleted!" -ForegroundColor Yellow
    $composeArgs += "-v"
}

if ($RemoveOrphans) {
    $composeArgs += "--remove-orphans"
}

Write-Host "Executing: docker compose $($composeArgs -join ' ')" -ForegroundColor Cyan
& docker compose @composeArgs

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n[OK] Local infrastructure successfully stopped." -ForegroundColor Green
} else {
    Write-Host "`n[ERROR] Failed to stop some containers." -ForegroundColor Red
    exit $LASTEXITCODE
}
