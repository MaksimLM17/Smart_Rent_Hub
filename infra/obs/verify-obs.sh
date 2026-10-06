#!/usr/bin/env bash
# Verification script for Smart Rent Hub Observability Stack (Profile obs: OTel Collector, Jaeger, Prometheus, Grafana, Loki, Alloy).

set -euo pipefail

PROMETHEUS_HOST="${PROMETHEUS_HOST:-localhost}"
PROMETHEUS_PORT="${PROMETHEUS_PORT:-9090}"
JAEGER_HOST="${JAEGER_HOST:-localhost}"
JAEGER_PORT="${JAEGER_PORT:-16686}"
OTEL_COLLECTOR_HOST="${OTEL_COLLECTOR_HOST:-localhost}"
OTEL_COLLECTOR_HEALTH_PORT="${OTEL_COLLECTOR_HEALTH_PORT:-13133}"
OTEL_COLLECTOR_METRICS_PORT="${OTEL_COLLECTOR_METRICS_PORT:-8889}"
LOKI_HOST="${LOKI_HOST:-localhost}"
LOKI_PORT="${LOKI_PORT:-3100}"
ALLOY_HOST="${ALLOY_HOST:-localhost}"
ALLOY_PORT="${ALLOY_PORT:-12345}"
GRAFANA_HOST="${GRAFANA_HOST:-localhost}"
GRAFANA_PORT="${GRAFANA_PORT:-3000}"

echo "============================================================"
echo " [Smart Rent Hub] Observability Stack Verification (T1.6)   "
echo "============================================================"

PASS_COUNT=0
FAIL_COUNT=0

check() {
    local name="$1"
    local cmd="$2"
    printf "Checking %s... " "$name"
    if eval "$cmd" >/dev/null 2>&1; then
        echo -e "\033[32m[PASS]\033[0m"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo -e "\033[31m[FAIL]\033[0m"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
}

# 1. Docker containers status
check "Docker containers status (profile obs)" \
    "for c in srh-jaeger srh-otel-collector srh-prometheus srh-loki srh-alloy srh-grafana; do [ \"\$(docker inspect -f '{{.State.Status}}' \$c 2>/dev/null)\" = 'running' ] || exit 1; done"

# 2. Jaeger UI
check "Jaeger Web UI (port $JAEGER_PORT)" \
    "curl -sf http://${JAEGER_HOST}:${JAEGER_PORT}/ | grep -q 'Jaeger'"

# 3. OTel Collector Health
check "OTel Collector Health (port $OTEL_COLLECTOR_HEALTH_PORT)" \
    "curl -sf http://${OTEL_COLLECTOR_HOST}:${OTEL_COLLECTOR_HEALTH_PORT}/ | grep -q 'Server available'"

# 4. OTel Collector Prometheus Exporter
check "OTel Collector Prometheus Exporter (port $OTEL_COLLECTOR_METRICS_PORT)" \
    "curl -sf http://${OTEL_COLLECTOR_HOST}:${OTEL_COLLECTOR_METRICS_PORT}/metrics"

# 5. Prometheus Health
check "Prometheus Health (port $PROMETHEUS_PORT)" \
    "curl -sf http://${PROMETHEUS_HOST}:${PROMETHEUS_PORT}/-/healthy | grep -q 'Prometheus Server is Healthy'"

# 6. Prometheus Targets API
check "Prometheus Targets API" \
    "curl -sf http://${PROMETHEUS_HOST}:${PROMETHEUS_PORT}/api/v1/targets | grep -q '\"status\":\"success\"'"

# 7. Loki Readiness
check "Loki Readiness (port $LOKI_PORT)" \
    "curl -sf http://${LOKI_HOST}:${LOKI_PORT}/ready | grep -q 'ready'"

# 8. Loki Log Push
NANO_TIME="$(date +%s%N)"
check "Loki Log Ingestion" \
    "curl -sf -X POST http://${LOKI_HOST}:${LOKI_PORT}/loki/api/v1/push -H 'Content-Type: application/json' -d '{\"streams\":[{\"stream\":{\"service\":\"obs-verifier\",\"level\":\"INFO\"},\"values\":[[\"${NANO_TIME}\",\"{\\\"level\\\":\\\"INFO\\\",\\\"service\\\":\\\"obs-verifier\\\",\\\"message\\\":\\\"Verification test log line\\\"}\"]]}]}'"

# 9. Grafana Alloy Readiness
check "Grafana Alloy Readiness (port $ALLOY_PORT)" \
    "curl -sf http://${ALLOY_HOST}:${ALLOY_PORT}/-/ready | grep -q 'ready'"

# 10. Grafana Health API
check "Grafana Health API (port $GRAFANA_PORT)" \
    "curl -sf http://${GRAFANA_HOST}:${GRAFANA_PORT}/api/health | grep -q '\"database\":\"ok\"'"

# 11. Grafana Datasources Provisioning
check "Grafana Datasources (Prometheus, Loki, Jaeger)" \
    "curl -sf -u admin:admin http://${GRAFANA_HOST}:${GRAFANA_PORT}/api/datasources | grep -q 'Prometheus' && curl -sf -u admin:admin http://${GRAFANA_HOST}:${GRAFANA_PORT}/api/datasources | grep -q 'Loki' && curl -sf -u admin:admin http://${GRAFANA_HOST}:${GRAFANA_PORT}/api/datasources | grep -q 'Jaeger'"

echo "------------------------------------------------------------"
echo "Results: PASS: ${PASS_COUNT}, FAIL: ${FAIL_COUNT}"

if [ "${FAIL_COUNT}" -gt 0 ]; then
    exit 1
fi
