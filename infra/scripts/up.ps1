<#
.SYNOPSIS
    Starts Smart Rent Hub local infrastructure with Docker Compose.
.DESCRIPTION
    Launches selected profiles (core, obs, or both), ensures environment configuration,
    monitors service health, and registers Kafka Connect Debezium connectors.
.PARAMETER Profiles
    List of compose profiles to start (e.g. 'core', 'obs'). Default is @('core').
.PARAMETER Obs
    Convenience switch to include the 'obs' (observability) profile.
.PARAMETER All
    Convenience switch to start all available profiles ('core' and 'obs').
.PARAMETER Build
    Force rebuild of container images (e.g. external-stubs).
.PARAMETER Foreground
    Run containers in foreground rather than detached mode.
.PARAMETER NoWait
    Do not wait for healthchecks to become healthy.
.PARAMETER NoRegisterConnectors
    Skip automatic registration of Debezium outbox connectors.
.EXAMPLE
    .\infra\scripts\up.ps1
    .\infra\scripts\up.ps1 -Obs
    .\infra\scripts\up.ps1 -All -Build
#>

[CmdletBinding()]
param(
    [string[]]$Profiles = @("core"),
    [switch]$Obs,
    [switch]$All,
    [switch]$Build,
    [switch]$Foreground,
    [switch]$NoWait,
    [switch]$NoRegisterConnectors
)

$ErrorActionPreference = "Stop"

# Resolve root repository directory
$rootDir = (Resolve-Path "$PSScriptRoot/../..").Path

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " [Smart Rent Hub] Starting Local Infrastructure             " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Check Docker installation and daemon status
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Docker CLI not found. Please install Docker Desktop." -ForegroundColor Red
    exit 1
}

$prevEAP = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
$null = docker info 2>&1
$isDockerRunning = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = $prevEAP

if (-not $isDockerRunning) {
    Write-Host "[ERROR] Docker daemon is not running!" -ForegroundColor Red
    Write-Host "Please start Docker Desktop and wait until the Docker engine is ready." -ForegroundColor Yellow
    exit 1
}

# 2. Check and initialize .env file
$envFile = Join-Path $rootDir ".env"
$envExampleFile = Join-Path $rootDir ".env.example"

if (-not (Test-Path $envFile)) {
    if (Test-Path $envExampleFile) {
        Write-Host "Creating .env from .env.example..." -ForegroundColor Yellow
        Copy-Item -Path $envExampleFile -Destination $envFile
    } else {
        Write-Host "[WARNING] Neither .env nor .env.example was found in $rootDir" -ForegroundColor Yellow
    }
}

# 3. Determine active profiles
$activeProfiles = [System.Collections.Generic.List[string]]::new()
foreach ($p in $Profiles) {
    if (-not $activeProfiles.Contains($p.ToLower())) {
        $activeProfiles.Add($p.ToLower())
    }
}

if ($Obs -and -not $activeProfiles.Contains("obs")) {
    $activeProfiles.Add("obs")
}

if ($All) {
    foreach ($p in @("core", "obs")) {
        if (-not $activeProfiles.Contains($p)) {
            $activeProfiles.Add($p)
        }
    }
}

Write-Host "Active Profiles: $($activeProfiles -join ', ')" -ForegroundColor Green

# 4. Construct Docker Compose arguments
$composeFile = Join-Path $rootDir "docker-compose.yml"
$composeArgs = @("-f", $composeFile)

foreach ($p in $activeProfiles) {
    $composeArgs += @("--profile", $p)
}

$composeArgs += "up"

if (-not $Foreground) {
    $composeArgs += "-d"
}

if ($Build) {
    $composeArgs += "--build"
}

Write-Host "Executing: docker compose $($composeArgs -join ' ')" -ForegroundColor Cyan
& docker compose @composeArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Docker compose failed to start services." -ForegroundColor Red
    exit $LASTEXITCODE
}

# 5. Wait for healthy status if detached and not skipped
if (-not $Foreground -and -not $NoWait) {
    Write-Host "`nWaiting for services to become healthy..." -ForegroundColor Yellow
    $maxWaitSec = 90
    $startTime = Get-Date

    # Collect containers launched by compose
    $containers = docker compose -f $composeFile $(foreach ($p in $activeProfiles) { @("--profile", $p) }) ps -q
    if ($containers) {
        $pending = $true
        while ($pending -and ((Get-Date) - $startTime).TotalSeconds -lt $maxWaitSec) {
            Start-Sleep -Seconds 3
            $unhealthyCount = 0
            $startingCount = 0

            foreach ($cid in $containers) {
                $health = docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $cid 2>$null
                if ($health -eq "starting") {
                    $startingCount++
                } elseif ($health -eq "unhealthy") {
                    $unhealthyCount++
                }
            }

            if ($startingCount -eq 0) {
                $pending = $false
            } else {
                Write-Host -NoNewline "."
            }
        }
        Write-Host ""
    }
    Write-Host "[OK] Infrastructure services initialized." -ForegroundColor Green
}

# 6. Auto-register Debezium connectors if core profile is active
if ($activeProfiles.Contains("core") -and -not $NoRegisterConnectors) {
    $registerScript = Join-Path $rootDir "infra/debezium/register-connectors.ps1"
    if (Test-Path $registerScript) {
        Write-Host "`nRegistering Debezium outbox connectors..." -ForegroundColor Cyan
        try {
            & powershell -ExecutionPolicy Bypass -File $registerScript -ConnectUrl "http://localhost:8083" -WaitTimeoutSec 60
        } catch {
            Write-Host "[WARNING] Connector registration warning: $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }
}

# 7. Print summary cheat sheet
Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host " [Smart Rent Hub] Services Cheat Sheet                       " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

if ($activeProfiles.Contains("core")) {
    Write-Host " [CORE Services]" -ForegroundColor Green
    Write-Host "  PostgreSQL:      localhost:5432 (user: postgres, pass: postgres)"
    Write-Host "  Redis:           localhost:6379"
    Write-Host "  MinIO API:       http://localhost:9000"
    Write-Host "  MinIO Console:   http://localhost:9001 (minioadmin / minioadmin)"
    Write-Host "  Keycloak:        http://localhost:8081 (admin / admin, realm: smartrent)"
    Write-Host "  Kafka Broker:    localhost:9092 (internal), localhost:9094 (host)"
    Write-Host "  Kafka Connect:   http://localhost:8083"
    Write-Host "  External Stubs:  http://localhost:8089 (/actuator/health)"
}

if ($activeProfiles.Contains("obs")) {
    Write-Host "`n [OBSERVABILITY Services]" -ForegroundColor Magenta
    Write-Host "  Jaeger UI:       http://localhost:16686"
    Write-Host "  Prometheus:      http://localhost:9090"
    Write-Host "  Grafana:         http://localhost:3000 (admin / admin)"
    Write-Host "  Loki API:        http://localhost:3100"
    Write-Host "  Alloy UI:        http://localhost:12345"
    Write-Host "  OTel Collector:  localhost:4317 (gRPC), localhost:4318 (HTTP)"
}

Write-Host "`nRun `.\infra\scripts\down.ps1` to stop services." -ForegroundColor DarkGray
Write-Host "Run `.\infra\scripts\reset.ps1` to stop services and wipe all data volumes." -ForegroundColor DarkGray
Write-Host "============================================================`n" -ForegroundColor Cyan
