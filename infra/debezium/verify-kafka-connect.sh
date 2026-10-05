#!/bin/sh
set -e

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== 1. Checking Kafka Connect Availability ==="
connect_info=$(curl -sf "${CONNECT_URL}/") || { echo "Error: Kafka Connect is not reachable at ${CONNECT_URL}"; exit 1; }
echo "Kafka Connect is up and running: OK"

echo ""
echo "=== 2. Checking Debezium PostgreSQL Connector Plugin ==="
plugins=$(curl -sf "${CONNECT_URL}/connector-plugins")
if echo "$plugins" | grep -q "io.debezium.connector.postgresql.PostgresConnector"; then
    echo "Found plugin: io.debezium.connector.postgresql.PostgresConnector"
    echo "Debezium PostgreSQL plugin loaded: OK"
else
    echo "Error: Debezium PostgreSQL connector plugin NOT found in Kafka Connect!"
    exit 1
fi

echo ""
echo "=== 3. Running Connector Registration Script ==="
sh "${SCRIPT_DIR}/register-connectors.sh"

echo ""
echo "=== 4. Checking Registered Connectors Status ==="
connectors=$(curl -sf "${CONNECT_URL}/connectors")
echo "Registered connectors: $connectors"

if echo "$connectors" | grep -q "booking-outbox"; then
    echo "Connector 'booking-outbox' present: OK"
else
    echo "Error: 'booking-outbox' not found in registered connectors"
    exit 1
fi

echo ""
echo ">>> ALL T1.4 CHECKS PASSED SUCCESSFULLY! <<<"
