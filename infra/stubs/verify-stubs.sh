#!/usr/bin/env bash
# Verification script for T1.5 External Stubs service
set -euo pipefail

PORT="${EXTERNAL_STUBS_PORT:-8089}"
BASE_URL="http://localhost:${PORT}"

echo "=== 1. Checking external-stubs Actuator Health (${BASE_URL}/actuator/health) ==="
HEALTH_STATUS=$(curl -sf "${BASE_URL}/actuator/health" | grep -o '"status":"UP"' || true)
if [ -z "$HEALTH_STATUS" ]; then
    echo "ERROR: Health check failed for ${BASE_URL}/actuator/health"
    exit 1
fi
echo "Actuator Health: UP"

echo -e "\n=== 2. Testing SMS Gateway Stub ==="
# Clear history
curl -sf -X DELETE "${BASE_URL}/api/stubs/sms" > /dev/null
echo "Cleared SMS history: OK"

# Send SMS
SEND_RESP=$(curl -sf -X POST "${BASE_URL}/api/stubs/sms/send" \
    -H "Content-Type: application/json" \
    -d '{"phoneNumber": "+79991234567", "message": "Your Smart Rent Hub code is 482019"}')
echo "$SEND_RESP" | grep -q '"code":"482019"' || { echo "ERROR: Code 482019 not found in response"; exit 1; }
echo "SMS Send & Code extraction: OK"

# Retrieve last SMS
LAST_RESP=$(curl -sf "${BASE_URL}/api/stubs/sms/last?phoneNumber=%2B79991234567")
echo "$LAST_RESP" | grep -q '"code":"482019"' || { echo "ERROR: Last SMS mismatch"; exit 1; }
echo "SMS Last Retrieval: OK"

echo -e "\n=== 3. Testing Bank ID / Identity Provider Stub ==="
# Default verification
BANK_RESP=$(curl -sf -X POST "${BASE_URL}/api/stubs/bank-id/verify" \
    -H "Content-Type: application/json" \
    -d '{"clientId": "client-test-001", "passportNumber": "4510 998877", "fullName": "Ivan Ivanov"}')
echo "$BANK_RESP" | grep -q '"status":"VERIFIED"' || { echo "ERROR: Bank ID verification failed"; exit 1; }
echo "Default Bank ID verification: OK"

# Custom scenario: REJECTED
curl -sf -X POST "${BASE_URL}/api/stubs/bank-id/scenarios" \
    -H "Content-Type: application/json" \
    -d '{"clientId": "client-fraud", "status": "REJECTED", "reason": "Passport is blacklisted"}' > /dev/null

FRAUD_RESP=$(curl -sf -X POST "${BASE_URL}/api/stubs/bank-id/verify" \
    -H "Content-Type: application/json" \
    -d '{"clientId": "client-fraud"}')
echo "$FRAUD_RESP" | grep -q '"status":"REJECTED"' || { echo "ERROR: Custom scenario failed"; exit 1; }
echo "Bank ID custom scenario (REJECTED): OK"

echo -e "\n=== 4. Testing Payment Gateway Stub ==="
PAY_RESP=$(curl -sf -X POST "${BASE_URL}/api/stubs/payments/authorize" \
    -H "Content-Type: application/json" \
    -d '{"bookingId": "booking-auto-01", "amount": 2500.00, "currency": "RUB", "cardNumber": "4000000000000001"}')
echo "$PAY_RESP" | grep -q '"status":"SUCCESS"' || { echo "ERROR: Payment authorization failed"; exit 1; }
echo "Payment authorization (SUCCESS): OK"

# Custom scenario: INSUFFICIENT_FUNDS
curl -sf -X POST "${BASE_URL}/api/stubs/payments/scenarios" \
    -H "Content-Type: application/json" \
    -d '{"bookingId": "booking-no-funds", "status": "INSUFFICIENT_FUNDS"}' > /dev/null

NOFUNDS_RESP=$(curl -sf -X POST "${BASE_URL}/api/stubs/payments/authorize" \
    -H "Content-Type: application/json" \
    -d '{"bookingId": "booking-no-funds", "amount": 99999.00}')
echo "$NOFUNDS_RESP" | grep -q '"status":"INSUFFICIENT_FUNDS"' || { echo "ERROR: Insufficient funds scenario failed"; exit 1; }
echo "Payment custom scenario (INSUFFICIENT_FUNDS): OK"

echo -e "\n===================================================="
echo " ALL T1.5 EXTERNAL-STUBS CHECKS PASSED SUCCESSFULLY!"
echo "===================================================="
