# Script to register Debezium CDC connectors in Kafka Connect

param(
    [string]$ConnectUrl = "http://localhost:8083",
    [string]$ConnectorsDir = "$PSScriptRoot/connectors"
)

$ErrorActionPreference = "Stop"

Write-Host ">>> Checking Kafka Connect readiness at $ConnectUrl..." -ForegroundColor Cyan

$maxRetries = 30
$retryCount = 0
$ready = $false

while (-not $ready -and $retryCount -lt $maxRetries) {
    try {
        $res = Invoke-RestMethod -Uri "$ConnectUrl/" -Method Get -TimeoutSec 3 -ErrorAction Stop
        if ($res.version) {
            $ready = $true
            Write-Host "Kafka Connect is ready (version: $($res.version), commit: $($res.commit))" -ForegroundColor Green
            break
        }
    } catch {
        $retryCount++
        Write-Host "Waiting for Kafka Connect... ($retryCount/$maxRetries)"
        Start-Sleep -Seconds 2
    }
}

if (-not $ready) {
    throw "Kafka Connect is not available at $ConnectUrl after $maxRetries attempts."
}

# Find all connector JSON files
$connectorFiles = Get-ChildItem -Path $ConnectorsDir -Filter "*.json"

if ($connectorFiles.Count -eq 0) {
    Write-Warning "No connector configuration files found in $ConnectorsDir"
    exit 0
}

Write-Host "`n>>> Registering Debezium connectors..." -ForegroundColor Cyan

foreach ($file in $connectorFiles) {
    $json = Get-Content -Raw $file.FullName | ConvertFrom-Json
    $connectorName = $json.name
    $configJson = $json.config | ConvertTo-Json -Depth 10

    Write-Host "Registering connector: '$connectorName' from $($file.Name)..." -NoNewline

    try {
        $regUrl = "$ConnectUrl/connectors/$connectorName/config"
        $regResponse = Invoke-RestMethod -Uri $regUrl -Method Put -Body $configJson -ContentType "application/json"
        Write-Host " [OK]" -ForegroundColor Green
    } catch {
        Write-Host " [FAILED]" -ForegroundColor Red
        Write-Error "Failed to register connector $connectorName: $_"
    }
}

Write-Host "`n>>> Currently registered connectors:" -ForegroundColor Cyan
$registered = Invoke-RestMethod -Uri "$ConnectUrl/connectors" -Method Get
foreach ($c in $registered) {
    $status = Invoke-RestMethod -Uri "$ConnectUrl/connectors/$c/status" -Method Get
    $state = $status.connector.state
    Write-Host " - $c : $state" -ForegroundColor $(if ($state -eq "RUNNING") { "Green" } else { "Yellow" })
}

Write-Host "`n>>> Connector registration completed." -ForegroundColor Green
