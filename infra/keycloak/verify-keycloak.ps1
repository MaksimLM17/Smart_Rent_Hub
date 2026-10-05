# Verification script for T1.3 Keycloak realm import and token acquisition

$ErrorActionPreference = "Stop"

$keycloakBaseUrl = "http://localhost:8081"
$tokenEndpoint = "$keycloakBaseUrl/realms/smartrent/protocol/openid-connect/token"
$realmEndpoint = "$keycloakBaseUrl/realms/smartrent"

Write-Host "=== 1. Checking Keycloak Realm Availability ===" -ForegroundColor Cyan
try {
    $realmResponse = Invoke-RestMethod -Uri $realmEndpoint -Method Get
    Write-Host "Realm name: $($realmResponse.realm) (expected: smartrent)"
    if ($realmResponse.realm -ne "smartrent") {
        throw "Realm name mismatch!"
    }
    Write-Host "Realm 'smartrent' is available: OK" -ForegroundColor Green
} catch {
    throw "Failed to reach Keycloak realm endpoint: $_"
}

function Decode-JwtPayload([string]$jwt) {
    $parts = $jwt.Split('.')
    if ($parts.Length -lt 2) { throw "Invalid JWT format" }
    $payloadBase64 = $parts[1]
    # Pad base64 string
    switch ($payloadBase64.Length % 4) {
        2 { $payloadBase64 += "==" }
        3 { $payloadBase64 += "=" }
    }
    $bytes = [System.Convert]::FromBase64String($payloadBase64)
    $json = [System.Text.Encoding]::UTF8.GetString($bytes)
    return ($json | ConvertFrom-Json)
}

Write-Host "`n=== 2. Testing Customer Token Acquisition (srh-customer) ===" -ForegroundColor Cyan
$customerBody = @{
    grant_type    = "password"
    client_id     = "srh-customer"
    client_secret = "srh-customer-secret"
    username      = "customer_test"
    password      = "password123"
}

$tokenResponse = Invoke-RestMethod -Uri $tokenEndpoint -Method Post -Body $customerBody -ContentType "application/x-www-form-urlencoded"
if (-not $tokenResponse.access_token) {
    throw "Customer token response did not contain access_token!"
}
$payload = Decode-JwtPayload -jwt $tokenResponse.access_token
Write-Host "Customer username: $($payload.preferred_username)"
Write-Host "Customer roles: $($payload.realm_access.roles -join ', ')"

if ($payload.realm_access.roles -notcontains "CUSTOMER") {
    throw "Role 'CUSTOMER' not found in token roles!"
}
Write-Host "Customer token with role 'CUSTOMER': OK" -ForegroundColor Green

Write-Host "`n=== 3. Testing Staff Token Acquisition (srh-staff: CATALOG_EDITOR) ===" -ForegroundColor Cyan
$staffBody = @{
    grant_type    = "password"
    client_id     = "srh-staff"
    client_secret = "srh-staff-secret"
    username      = "editor_test"
    password      = "password123"
}

$staffTokenResponse = Invoke-RestMethod -Uri $tokenEndpoint -Method Post -Body $staffBody -ContentType "application/x-www-form-urlencoded"
$staffPayload = Decode-JwtPayload -jwt $staffTokenResponse.access_token
Write-Host "Editor username: $($staffPayload.preferred_username)"
Write-Host "Editor roles: $($staffPayload.realm_access.roles -join ', ')"

if ($staffPayload.realm_access.roles -notcontains "CATALOG_EDITOR") {
    throw "Role 'CATALOG_EDITOR' not found in token roles!"
}
Write-Host "Staff token with role 'CATALOG_EDITOR': OK" -ForegroundColor Green

Write-Host "`n=== 4. Testing Admin Token Acquisition (srh-staff: ADMIN) ===" -ForegroundColor Cyan
$adminBody = @{
    grant_type    = "password"
    client_id     = "srh-staff"
    client_secret = "srh-staff-secret"
    username      = "admin_test"
    password      = "password123"
}

$adminTokenResponse = Invoke-RestMethod -Uri $tokenEndpoint -Method Post -Body $adminBody -ContentType "application/x-www-form-urlencoded"
$adminPayload = Decode-JwtPayload -jwt $adminTokenResponse.access_token
Write-Host "Admin username: $($adminPayload.preferred_username)"
Write-Host "Admin roles: $($adminPayload.realm_access.roles -join ', ')"

if ($adminPayload.realm_access.roles -notcontains "ADMIN") {
    throw "Role 'ADMIN' not found in token roles!"
}
Write-Host "Admin token with role 'ADMIN': OK" -ForegroundColor Green

Write-Host "`n=== 5. Testing Service Account Token (Client Credentials) ===" -ForegroundColor Cyan
$svcBody = @{
    grant_type    = "client_credentials"
    client_id     = "srh-service-client"
    client_secret = "srh-service-secret"
}

$svcTokenResponse = Invoke-RestMethod -Uri $tokenEndpoint -Method Post -Body $svcBody -ContentType "application/x-www-form-urlencoded"
if (-not $svcTokenResponse.access_token) {
    throw "Service token response did not contain access_token!"
}
Write-Host "Service token acquisition: OK" -ForegroundColor Green

Write-Host "`n>>> ALL T1.3 CHECKS PASSED SUCCESSFULLY! <<<" -ForegroundColor Green
