# Verification script for T1.1 PostgreSQL and privilege separation

$ErrorActionPreference = "Stop"

Write-Host "=== 1. Checking PostgreSQL parameters ===" -ForegroundColor Cyan
$walLevel = (docker exec srh-postgres psql -U postgres -t -A -c "SHOW wal_level;").Trim()
$slots = (docker exec srh-postgres psql -U postgres -t -A -c "SHOW max_replication_slots;").Trim()
$senders = (docker exec srh-postgres psql -U postgres -t -A -c "SHOW max_wal_senders;").Trim()

Write-Host "wal_level: $walLevel (expected: logical)"
Write-Host "max_replication_slots: $slots (expected: >= 10)"
Write-Host "max_wal_senders: $senders (expected: >= 10)"

if ($walLevel -ne "logical") { throw "wal_level is not logical" }
if ([int]$slots -lt 10) { throw "max_replication_slots < 10" }
if ([int]$senders -lt 10) { throw "max_wal_senders < 10" }

Write-Host "`n=== 2. Checking required databases ===" -ForegroundColor Cyan
$expectedDbs = @("booking", "inventory", "payments", "risk", "notification", "ai", "finance", "keycloak", "litellm")
$existingDbs = (docker exec srh-postgres psql -U postgres -t -A -c "SELECT datname FROM pg_database WHERE datistemplate = false;").Split("`r`n") | Where-Object { $_ -ne "" }

foreach ($db in $expectedDbs) {
    if ($existingDbs -contains $db) {
        Write-Host "Database '$db': OK" -ForegroundColor Green
    } else {
        throw "Database '$db' not found!"
    }
}

Write-Host "`n=== 3. Checking system roles ===" -ForegroundColor Cyan
$debeziumReplication = (docker exec srh-postgres psql -U postgres -t -A -c "SELECT rolreplication FROM pg_roles WHERE rolname = 'debezium';").Trim()
if ($debeziumReplication -ne "t") { throw "User debezium does not have REPLICATION role!" }
Write-Host "Role 'debezium' with REPLICATION: OK" -ForegroundColor Green

# Verify Keycloak connection
$kcCheck = (docker exec -e PGPASSWORD=keycloak_pass srh-postgres psql -U keycloak -d keycloak -t -A -c "SELECT 1;").Trim()
if ($kcCheck -ne "1") { throw "Keycloak user cannot connect to keycloak db!" }
Write-Host "User 'keycloak' connection: OK" -ForegroundColor Green

# Verify LiteLLM connection
$litellmCheck = (docker exec -e PGPASSWORD=litellm_pass srh-postgres psql -U litellm -d litellm -t -A -c "SELECT 1;").Trim()
if ($litellmCheck -ne "1") { throw "LiteLLM user cannot connect to litellm db!" }
Write-Host "User 'litellm' connection: OK" -ForegroundColor Green

Write-Host "`n=== 4. Checking privilege separation for business services ===" -ForegroundColor Cyan
$services = @("booking", "inventory", "payments", "risk", "notification", "ai", "finance")

foreach ($svc in $services) {
    Write-Host "--- Service '$svc' ---" -ForegroundColor Yellow
    $owner = "${svc}_owner"
    $app = "${svc}_app"
    $ownerPass = "${svc}_owner_pass"
    $appPass = "${svc}_app_pass"

    # A. Owner can CREATE TABLE
    docker exec -e PGPASSWORD=$ownerPass srh-postgres psql -U $owner -d $svc -c "CREATE TABLE IF NOT EXISTS test_privs (id int, val text);" | Out-Null
    Write-Host "[$svc] Owner DDL (CREATE TABLE): OK" -ForegroundColor Green

    # B. App user can INSERT and SELECT
    docker exec -e PGPASSWORD=$appPass srh-postgres psql -U $app -d $svc -c "INSERT INTO test_privs (id, val) VALUES (1, 'srh-test');" | Out-Null
    $val = (docker exec -e PGPASSWORD=$appPass srh-postgres psql -U $app -d $svc -t -A -c "SELECT val FROM test_privs WHERE id = 1;").Trim()
    if ($val -ne "srh-test") { throw "[$svc] App user failed to SELECT inserted row" }
    Write-Host "[$svc] App DML (INSERT & SELECT): OK" -ForegroundColor Green

    # C. App user CANNOT CREATE TABLE (must fail with permission denied)
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    docker exec -e PGPASSWORD=$appPass srh-postgres psql -U $app -d $svc -c "CREATE TABLE app_forbidden (id int);" 2>&1 | Out-Null
    $ddlExit = $LASTEXITCODE
    $ErrorActionPreference = $prevEAP

    if ($ddlExit -eq 0) {
        throw "[$svc] SECURITY VIOLATION: App user was able to CREATE TABLE!"
    }
    Write-Host "[$svc] App DDL forbidden (CREATE TABLE rejected): OK" -ForegroundColor Green

    # D. Debezium can SELECT from table
    $debVal = (docker exec -e PGPASSWORD=debezium_pass srh-postgres psql -U debezium -d $svc -t -A -c "SELECT val FROM test_privs WHERE id = 1;").Trim()
    if ($debVal -ne "srh-test") { throw "[$svc] Debezium failed to SELECT from table" }
    Write-Host "[$svc] Debezium read access: OK" -ForegroundColor Green

    # Clean up test table
    docker exec -e PGPASSWORD=$ownerPass srh-postgres psql -U $owner -d $svc -c "DROP TABLE test_privs;" | Out-Null
    Write-Host "[$svc] Owner cleanup (DROP TABLE): OK" -ForegroundColor Green
}

Write-Host "`n>>> ALL T1.1 CHECKS PASSED SUCCESSFULLY! <<<" -ForegroundColor Green
