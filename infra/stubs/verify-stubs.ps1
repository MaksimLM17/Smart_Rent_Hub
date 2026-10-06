# Verification script for T1.5 External Stubs service
# Verifies Actuator Health, SMS Gateway, Bank ID Provider, and Payment Gateway stubs.

$ErrorActionPreference = "Stop"

$port = if ($env:EXTERNAL_STUBS_PORT) { $env:EXTERNAL_STUBS_PORT } else { "8089" }
$baseUrl = "http://localhost:$port"

Write-Host "=== 1. Checking external-stubs Actuator Health ($baseUrl/actuator/health) ===" -ForegroundColor Cyan
try {
    $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method Get
    if ($health.status -ne "UP") {
        throw "Health status is not UP: $($health.status)"
    }
    Write-Host "Actuator Health: UP" -ForegroundColor Green
} catch {
    throw "Failed to connect to external-stubs at $baseUrl. Ensure container or app is running on port $port. Error: $_"
}

Write-Host "`n=== 2. Testing SMS Gateway Stub ===" -ForegroundColor Cyan
# Clear history
Invoke-RestMethod -Uri "$baseUrl/api/stubs/sms" -Method Delete | Out-Null
Write-Host "Cleared SMS history: OK"

# Send SMS
$smsPayload = @{
    phoneNumber = "+79991234567"
    message = "Your Smart Rent Hub code is 482019"
} | ConvertTo-Json

$sentSms = Invoke-RestMethod -Uri "$baseUrl/api/stubs/sms/send" -Method Post -Body $smsPayload -ContentType "application/json"
if ($sentSms.status -ne "SENT" -or $sentSms.code -ne "482019") {
    throw "SMS send failed or code mismatch: got $($sentSms.code), expected 482019"
}
Write-Host "SMS Send & Code extraction: OK (code: $($sentSms.code))" -ForegroundColor Green

# Retrieve last SMS
$lastSms = Invoke-RestMethod -Uri "$baseUrl/api/stubs/sms/last?phoneNumber=%2B79991234567" -Method Get
if ($lastSms.code -ne "482019") {
    throw "Last SMS mismatch: got $($lastSms.code), expected 482019"
}
Write-Host "SMS Last Retrieval: OK" -ForegroundColor Green

Write-Host "`n=== 3. Testing Bank ID / Identity Provider Stub ===" -ForegroundColor Cyan
# Default verification
$bankIdPayload = @{
    clientId = "client-test-001"
    passportNumber = "4510 998877"
    fullName = "Ivan Ivanov"
} | ConvertTo-Json

$bankIdResp = Invoke-RestMethod -Uri "$baseUrl/api/stubs/bank-id/verify" -Method Post -Body ([System.Text.Encoding]::UTF8.GetBytes($bankIdPayload)) -ContentType "application/json; charset=utf-8"
if ($bankIdResp.status -ne "VERIFIED") {
    throw "Bank ID verification failed: got $($bankIdResp.status), expected VERIFIED"
}
Write-Host "Default Bank ID verification: OK (status: $($bankIdResp.status))" -ForegroundColor Green

# Custom scenario: REJECTED
$rejectedScenario = @{
    clientId = "client-fraud"
    status = "REJECTED"
    reason = "Passport is blacklisted"
    score = 0.05
} | ConvertTo-Json

Invoke-RestMethod -Uri "$baseUrl/api/stubs/bank-id/scenarios" -Method Post -Body $rejectedScenario -ContentType "application/json" | Out-Null

$fraudPayload = @{ clientId = "client-fraud" } | ConvertTo-Json
$fraudResp = Invoke-RestMethod -Uri "$baseUrl/api/stubs/bank-id/verify" -Method Post -Body $fraudPayload -ContentType "application/json"
if ($fraudResp.status -ne "REJECTED") {
    throw "Bank ID custom scenario failed: got $($fraudResp.status), expected REJECTED"
}
Write-Host "Bank ID custom scenario (REJECTED): OK" -ForegroundColor Green

Write-Host "`n=== 4. Testing Payment Gateway Stub ===" -ForegroundColor Cyan
$payPayload = @{
    bookingId = "booking-auto-01"
    amount = 2500.00
    currency = "RUB"
    cardNumber = "4000000000000001"
} | ConvertTo-Json

$payResp = Invoke-RestMethod -Uri "$baseUrl/api/stubs/payments/authorize" -Method Post -Body $payPayload -ContentType "application/json"
if ($payResp.status -ne "SUCCESS") {
    throw "Payment authorization failed: got $($payResp.status), expected SUCCESS"
}
Write-Host "Payment authorization (SUCCESS): OK (txn: $($payResp.transactionId))" -ForegroundColor Green

# Custom scenario: INSUFFICIENT_FUNDS
$payScenario = @{
    bookingId = "booking-no-funds"
    status = "INSUFFICIENT_FUNDS"
    message = "Not enough funds"
} | ConvertTo-Json

Invoke-RestMethod -Uri "$baseUrl/api/stubs/payments/scenarios" -Method Post -Body $payScenario -ContentType "application/json" | Out-Null

$noFundsPayload = @{
    bookingId = "booking-no-funds"
    amount = 99999.00
} | ConvertTo-Json

$noFundsResp = Invoke-RestMethod -Uri "$baseUrl/api/stubs/payments/authorize" -Method Post -Body $noFundsPayload -ContentType "application/json"
if ($noFundsResp.status -ne "INSUFFICIENT_FUNDS") {
    throw "Payment custom scenario failed: got $($noFundsResp.status), expected INSUFFICIENT_FUNDS"
}
Write-Host "Payment custom scenario (INSUFFICIENT_FUNDS): OK" -ForegroundColor Green

Write-Host "`n====================================================" -ForegroundColor Green
Write-Host " ALL T1.5 EXTERNAL-STUBS CHECKS PASSED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "====================================================" -ForegroundColor Green
