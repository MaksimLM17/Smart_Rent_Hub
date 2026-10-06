<#
.SYNOPSIS
    Verification script for Smart Rent Hub Observability Stack (Profile obs: OTel Collector, Jaeger, Prometheus, Grafana, Loki, Alloy).
.DESCRIPTION
    Checks health endpoints, ports, container statuses, log ingestion in Loki and datasources in Grafana.
#>

[CmdletBinding()]
param(
    [string]$PrometheusHost = "localhost",
    [int]$PrometheusPort = 9090,
    [string]$JaegerHost = "localhost",
    [int]$JaegerPort = 16686,
    [string]$OtelCollectorHost = "localhost",
    [int]$OtelCollectorHealthPort = 13133,
    [int]$OtelCollectorMetricsPort = 8889,
    [string]$LokiHost = "localhost",
    [int]$LokiPort = 3100,
    [string]$AlloyHost = "localhost",
    [int]$AlloyPort = 12345,
    [string]$GrafanaHost = "localhost",
    [int]$GrafanaPort = 3000
)

$ErrorActionPreference = "Continue"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " [Smart Rent Hub] Observability Stack Verification (T1.6)   " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$passCount = 0
$failCount = 0

function Test-Check {
    param(
        [string]$Name,
        [scriptblock]$Script
    )
    Write-Host -NoNewline "Checking $Name... "
    try {
        $result = & $Script
        if ($result -eq $true) {
            Write-Host "[PASS]" -ForegroundColor Green
            $script:passCount++
        } else {
            Write-Host "[FAIL]" -ForegroundColor Red
            $script:failCount++
        }
    } catch {
        Write-Host "[FAIL] ($($_.Exception.Message))" -ForegroundColor Red
        $script:failCount++
    }
}

# 1. Container status check
Test-Check "Docker containers status (profile obs)" {
    $containers = @("srh-jaeger", "srh-otel-collector", "srh-prometheus", "srh-loki", "srh-alloy", "srh-grafana")
    $allRunning = $true
    foreach ($c in $containers) {
        $status = docker inspect -f '{{.State.Status}}' $c 2>$null
        if ($status -ne "running") {
            Write-Host -NoNewline "[$c not running: $status] "
            $allRunning = $false
        }
    }
    return $allRunning
}

# 2. Jaeger UI
Test-Check "Jaeger Web UI (port $JaegerPort)" {
    $resp = Invoke-WebRequest -Uri "http://${JaegerHost}:${JaegerPort}/" -UseBasicParsing -TimeoutSec 5
    return ($resp.StatusCode -eq 200 -and $resp.Content -match "Jaeger")
}

# 3. OTel Collector Health Check Extension
Test-Check "OTel Collector Health (port $OtelCollectorHealthPort)" {
    $resp = Invoke-WebRequest -Uri "http://${OtelCollectorHost}:${OtelCollectorHealthPort}/" -UseBasicParsing -TimeoutSec 5
    return ($resp.StatusCode -eq 200 -and $resp.Content -match "Server available")
}

# 4. OTel Collector Prometheus Exporter
Test-Check "OTel Collector Prometheus Exporter (port $OtelCollectorMetricsPort)" {
    $resp = Invoke-WebRequest -Uri "http://${OtelCollectorHost}:${OtelCollectorMetricsPort}/metrics" -UseBasicParsing -TimeoutSec 5
    return ($resp.StatusCode -eq 200)
}

# 5. Prometheus Server
Test-Check "Prometheus Health (port $PrometheusPort)" {
    $resp = Invoke-WebRequest -Uri "http://${PrometheusHost}:${PrometheusPort}/-/healthy" -UseBasicParsing -TimeoutSec 5
    return ($resp.StatusCode -eq 200 -and $resp.Content -match "Prometheus Server is Healthy")
}

# 6. Prometheus Targets API
Test-Check "Prometheus Targets API" {
    $resp = Invoke-RestMethod -Uri "http://${PrometheusHost}:${PrometheusPort}/api/v1/targets" -TimeoutSec 5
    return ($resp.status -eq "success")
}

# 7. Loki Readiness
Test-Check "Loki Readiness (port $LokiPort)" {
    $resp = Invoke-WebRequest -Uri "http://${LokiHost}:${LokiPort}/ready" -UseBasicParsing -TimeoutSec 5
    return ($resp.StatusCode -eq 200 -and $resp.Content -match "ready")
}

# 8. Loki Log Push & Query
Test-Check "Loki Log Ingestion & Query" {
    $nanoTime = [string]((Get-Date).ToUniversalTime().Ticks * 100) # approximation for nanoseconds
    $payload = @{
        streams = @(
            @{
                stream = @{
                    service = "obs-verifier"
                    level = "INFO"
                }
                values = @(
                    @($nanoTime, '{"level":"INFO","service":"obs-verifier","message":"Verification test log line"}')
                )
            }
        )
    } | ConvertTo-Json -Depth 5

    $pushResp = Invoke-WebRequest -Uri "http://${LokiHost}:${LokiPort}/loki/api/v1/push" -Method POST -Body $payload -ContentType "application/json" -TimeoutSec 5
    return ($pushResp.StatusCode -eq 204 -or $pushResp.StatusCode -eq 200)
}

# 9. Grafana Alloy Readiness
Test-Check "Grafana Alloy Readiness (port $AlloyPort)" {
    $resp = Invoke-WebRequest -Uri "http://${AlloyHost}:${AlloyPort}/-/ready" -UseBasicParsing -TimeoutSec 5
    return ($resp.StatusCode -eq 200 -and $resp.Content -match "ready")
}

# 10. Grafana Health API
Test-Check "Grafana Health API (port $GrafanaPort)" {
    $resp = Invoke-RestMethod -Uri "http://${GrafanaHost}:${GrafanaPort}/api/health" -TimeoutSec 5
    return ($resp.database -eq "ok")
}

# 11. Grafana Datasources Provisioning (Prometheus, Loki, Jaeger)
Test-Check "Grafana Datasources (Prometheus, Loki, Jaeger)" {
    $authHeader = @{
        Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:admin"))
    }
    $datasources = Invoke-RestMethod -Uri "http://${GrafanaHost}:${GrafanaPort}/api/datasources" -Headers $authHeader -TimeoutSec 5
    $names = $datasources | ForEach-Object { $_.name }
    $hasProm = $names -contains "Prometheus"
    $hasLoki = $names -contains "Loki"
    $hasJaeger = $names -contains "Jaeger"
    return ($hasProm -and $hasLoki -and $hasJaeger)
}

Write-Host "------------------------------------------------------------" -ForegroundColor Cyan
Write-Host "Results: PASS: $passCount, FAIL: $failCount" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Red" })

if ($failCount -gt 0) {
    exit 1
} else {
    exit 0
}
