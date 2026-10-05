#!/bin/sh
set -e

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONNECTORS_DIR="${SCRIPT_DIR}/connectors"

echo ">>> Checking Kafka Connect readiness at ${CONNECT_URL}..."

max_retries=30
retry_count=0
ready=0

while [ $ready -eq 0 ] && [ $retry_count -lt $max_retries ]; do
    if curl -sf "${CONNECT_URL}/" >/dev/null 2>&1; then
        ready=1
        echo "Kafka Connect is ready."
        break
    fi
    retry_count=$((retry_count + 1))
    echo "Waiting for Kafka Connect... (${retry_count}/${max_retries})"
    sleep 2
done

if [ $ready -eq 0 ]; then
    echo "Error: Kafka Connect is not available after ${max_retries} attempts."
    exit 1
fi

echo ""
echo ">>> Registering Debezium connectors..."

for file in "${CONNECTORS_DIR}"/*.json; do
    [ -f "$file" ] || continue
    name=$(grep -o '"name": *"[^"]*"' "$file" | head -1 | cut -d'"' -f4)
    config=$(sed -n '/"config": *{/,/}/p' "$file" | sed 's/"config": *//')

    echo "Registering connector: '$name'..."
    # Extract config body cleanly
    config_json=$(cat "$file" | grep -v '"name":' | sed '1s/^{/{/' | sed 's/"config": *//' | sed '$s/}$/}/' 2>/dev/null || cat "$file")
    
    # Use python/awk/jq fallback or directly send full file if json contains name & config
    # In Kafka Connect REST API: POST /connectors accepts {"name": "...", "config": {...}}
    # PUT /connectors/{name}/config accepts {...} (just config)
    # Sending full file to POST /connectors:
    http_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${CONNECT_URL}/connectors" \
        -H "Content-Type: application/json" \
        -d @"$file")

    if [ "$http_code" = "201" ] || [ "$http_code" = "409" ]; then
        echo " - Connector '$name': registered (HTTP $http_code)"
    else
        echo " - Connector '$name': status HTTP $http_code"
    fi
done

echo ""
echo ">>> Currently registered connectors:"
curl -s "${CONNECT_URL}/connectors"
echo ""
