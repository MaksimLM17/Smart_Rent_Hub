#!/bin/sh
set -e

KEYCLOAK_BASE_URL="${KEYCLOAK_BASE_URL:-http://localhost:8081}"
TOKEN_ENDPOINT="${KEYCLOAK_BASE_URL}/realms/smartrent/protocol/openid-connect/token"
REALM_ENDPOINT="${KEYCLOAK_BASE_URL}/realms/smartrent"

echo "=== 1. Checking Keycloak Realm Availability ==="
realm_name=$(curl -sf "$REALM_ENDPOINT" | grep -o '"realm":"[^"]*"' | cut -d'"' -f4)
echo "Realm name: $realm_name (expected: smartrent)"
[ "$realm_name" = "smartrent" ] || { echo "Error: Realm name mismatch"; exit 1; }
echo "Realm 'smartrent' is available: OK"

decode_jwt_payload() {
    jwt="$1"
    payload_b64=$(echo "$jwt" | cut -d'.' -f2)
    # Add padding if needed
    rem=$((${#payload_b64} % 4))
    if [ "$rem" -eq 2 ]; then
        payload_b64="${payload_b64}=="
    elif [ "$rem" -eq 3 ]; then
        payload_b64="${payload_b64}="
    fi
    echo "$payload_b64" | base64 -d 2>/dev/null
}

echo ""
echo "=== 2. Testing Customer Token Acquisition (srh-customer) ==="
resp=$(curl -sf -X POST "$TOKEN_ENDPOINT" \
    -d "grant_type=password" \
    -d "client_id=srh-customer" \
    -d "client_secret=srh-customer-secret" \
    -d "username=customer_test" \
    -d "password=password123")

token=$(echo "$resp" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
[ -n "$token" ] || { echo "Error: Failed to obtain customer token"; exit 1; }
payload=$(decode_jwt_payload "$token")
echo "$payload" | grep -q '"CUSTOMER"' || { echo "Error: Role CUSTOMER not found in token"; exit 1; }
echo "Customer token with role 'CUSTOMER': OK"

echo ""
echo "=== 3. Testing Staff Token Acquisition (srh-staff: CATALOG_EDITOR) ==="
resp=$(curl -sf -X POST "$TOKEN_ENDPOINT" \
    -d "grant_type=password" \
    -d "client_id=srh-staff" \
    -d "client_secret=srh-staff-secret" \
    -d "username=editor_test" \
    -d "password=password123")

token=$(echo "$resp" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
[ -n "$token" ] || { echo "Error: Failed to obtain editor token"; exit 1; }
payload=$(decode_jwt_payload "$token")
echo "$payload" | grep -q '"CATALOG_EDITOR"' || { echo "Error: Role CATALOG_EDITOR not found in token"; exit 1; }
echo "Staff token with role 'CATALOG_EDITOR': OK"

echo ""
echo "=== 4. Testing Admin Token Acquisition (srh-staff: ADMIN) ==="
resp=$(curl -sf -X POST "$TOKEN_ENDPOINT" \
    -d "grant_type=password" \
    -d "client_id=srh-staff" \
    -d "client_secret=srh-staff-secret" \
    -d "username=admin_test" \
    -d "password=password123")

token=$(echo "$resp" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
[ -n "$token" ] || { echo "Error: Failed to obtain admin token"; exit 1; }
payload=$(decode_jwt_payload "$token")
echo "$payload" | grep -q '"ADMIN"' || { echo "Error: Role ADMIN not found in token"; exit 1; }
echo "Admin token with role 'ADMIN': OK"

echo ""
echo "=== 5. Testing Service Account Token (Client Credentials) ==="
resp=$(curl -sf -X POST "$TOKEN_ENDPOINT" \
    -d "grant_type=client_credentials" \
    -d "client_id=srh-service-client" \
    -d "client_secret=srh-service-secret")

token=$(echo "$resp" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
[ -n "$token" ] || { echo "Error: Failed to obtain service account token"; exit 1; }
echo "Service token acquisition: OK"

echo ""
echo ">>> ALL T1.3 CHECKS PASSED SUCCESSFULLY! <<<"
