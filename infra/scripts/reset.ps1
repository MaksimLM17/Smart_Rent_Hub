<#
.SYNOPSIS
    Resets Smart Rent Hub local infrastructure from scratch (down -v -> up).
.DESCRIPTION
    Tears down all running containers, removes persistent volumes (PostgreSQL, Kafka,
    MinIO, Redis, Loki, Prometheus, Grafana), and starts fresh infrastructure instances.
.PARAMETER Profiles
    List of compose profiles to start after reset. Default is @('core').
.PARAMETER Obs
    Convenience switch to include the 'obs' (observability) profile after reset.
.PARAMETER All
    Convenience switch to start all available profiles ('core' and 'obs') after reset.
.PARAMETER Build
    Force rebuild of container images during restart.
.PARAMETER Force
    Skip interactive confirmation prompt before wiping all data.
.EXAMPLE
    .\infra\scripts\reset.ps1
    .\infra\scripts\reset.ps1 -Obs -Force
#>

[CmdletBinding()]
param(
    [string[]]$Profiles = @("core"),
    [switch]$Obs,
    [switch]$All,
    [switch]$Build,
    [switch]$Force
)

$ErrorActionPreference = "Stop"

# Resolve root repository directory
$rootDir = (Resolve-Path "$PSScriptRoot/../..").Path

Write-Host "============================================================" -ForegroundColor Yellow
Write-Host " [Smart Rent Hub] Resetting Local Infrastructure            " -ForegroundColor Yellow
Write-Host "============================================================" -ForegroundColor Yellow

if (-not $Force) {
    Write-Host "ATTENTION: This operation will STOP all containers and PERMANENTLY WIPE" -ForegroundColor Red
    Write-Host "all database and storage volumes (PostgreSQL, Redis, MinIO, Kafka, etc.).`n" -ForegroundColor Red
    $answer = Read-Host "Are you sure you want to proceed with full reset? (y/N)"
    if ($answer -ne "y" -and $answer -ne "Y") {
        Write-Host "Reset canceled by user." -ForegroundColor Gray
        exit 0
    }
}

Write-Host "`n1. Stopping containers and wiping volumes..." -ForegroundColor Cyan
$downScript = Join-Path $PSScriptRoot "down.ps1"
& $downScript -Volumes -Profiles @("core", "obs")

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Failed during teardown step." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`n2. Starting fresh infrastructure instances..." -ForegroundColor Cyan
$upScript = Join-Path $PSScriptRoot "up.ps1"

$upArgs = @{
    Profiles = $Profiles
}
if ($Obs) { $upArgs["Obs"] = $true }
if ($All) { $upArgs["All"] = $true }
if ($Build) { $upArgs["Build"] = $true }

& $upScript @upArgs

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n[OK] Local infrastructure successfully reset and initialized." -ForegroundColor Green
} else {
    Write-Host "`n[ERROR] Failed to start infrastructure during reset." -ForegroundColor Red
    exit $LASTEXITCODE
}
