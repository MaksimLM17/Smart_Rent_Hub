# Verification script for T1.4 Kafka KRaft and Kafka Connect with Debezium

$ErrorActionPreference = "Stop"

$connectUrl = "http://localhost:8083"

Write-Host "=== 1. Checking Kafka Connect Availability ===" -ForegroundColor Cyan
try {
    $rootInfo = Invoke-RestMethod -Uri "$connectUrl/" -Method Get
    Write-Host "Kafka Connect version: $($rootInfo.version)"
    Write-Host "Kafka cluster ID: $($rootInfo.kafka_cluster_id)"
    Write-Host "Kafka Connect is up and running: OK" -ForegroundColor Green
} catch {
    throw "Kafka Connect is not reachable at $connectUrl: $_"
}

Write-Host "`n=== 2. Checking Debezium PostgreSQL Connector Plugin ===" -ForegroundColor Cyan
try {
    $plugins = Invoke-RestMethod -Uri "$connectUrl/connector-plugins" -Method Get
    $postgresPlugin = $plugins | Where-Object { $_.class -eq "io.debezium.connector.postgresql.PostgresConnector" }

    if (-not $postgresPlugin) {
        Write-Host "Available plugins:"
        $plugins | ForEach-Object { Write-Host " - $($_.class)" }
        throw "io.debezium.connector.postgresql.PostgresConnector plugin is NOT loaded in Kafka Connect!"
    }

    Write-Host "Found plugin: $($postgresPlugin.class) (version: $($postgresPlugin.version))" -ForegroundColor Green
    Write-Host "Debezium PostgreSQL plugin loaded: OK" -ForegroundColor Green
} catch {
    throw "Failed to fetch connector plugins: $_"
}

Write-Host "`n=== 3. Checking Connector Registration Script ===" -ForegroundColor Cyan
& "$PSScriptRoot/register-connectors.ps1" -ConnectUrl $connectUrl

Write-Host "`n=== 4. Checking Registered Connectors Status ===" -ForegroundColor Cyan
$connectors = Invoke-RestMethod -Uri "$connectUrl/connectors" -Method Get
Write-Host "Total connectors registered: $($connectors.Count)"

if ($connectors -notcontains "booking-outbox") {
    throw "Expected connector 'booking-outbox' is not in registered connectors list!"
}
Write-Host "Connector 'booking-outbox' present: OK" -ForegroundColor Green

Write-Host "`n>>> ALL T1.4 CHECKS PASSED SUCCESSFULLY! <<<" -ForegroundColor Green
