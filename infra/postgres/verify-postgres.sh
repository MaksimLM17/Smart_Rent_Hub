#!/bin/sh
set -e

CONTAINER_NAME="${1:-srh-postgres}"

echo "=== 1. Checking PostgreSQL parameters ==="
wal_level=$(docker exec "$CONTAINER_NAME" psql -U postgres -t -A -c "SHOW wal_level;")
slots=$(docker exec "$CONTAINER_NAME" psql -U postgres -t -A -c "SHOW max_replication_slots;")
senders=$(docker exec "$CONTAINER_NAME" psql -U postgres -t -A -c "SHOW max_wal_senders;")

echo "wal_level: $wal_level (expected: logical)"
echo "max_replication_slots: $slots (expected: >= 10)"
echo "max_wal_senders: $senders (expected: >= 10)"

[ "$wal_level" = "logical" ] || { echo "Error: wal_level is not logical"; exit 1; }
[ "$slots" -ge 10 ] || { echo "Error: max_replication_slots < 10"; exit 1; }
[ "$senders" -ge 10 ] || { echo "Error: max_wal_senders < 10"; exit 1; }

echo ""
echo "=== 2. Checking required databases ==="
expected_dbs="booking inventory payments risk notification ai finance keycloak litellm"
existing_dbs=$(docker exec "$CONTAINER_NAME" psql -U postgres -t -A -c "SELECT datname FROM pg_database WHERE datistemplate = false;")

for db in $expected_dbs; do
    if echo "$existing_dbs" | grep -qw "$db"; then
        echo "Database '$db': OK"
    else
        echo "Error: Database '$db' not found!"
        exit 1
    fi
done

echo ""
echo "=== 3. Checking system roles ==="
debezium_rep=$(docker exec "$CONTAINER_NAME" psql -U postgres -t -A -c "SELECT rolreplication FROM pg_roles WHERE rolname = 'debezium';")
[ "$debezium_rep" = "t" ] || { echo "Error: debezium role has no REPLICATION privilege"; exit 1; }
echo "Role 'debezium' with REPLICATION: OK"

docker exec -e PGPASSWORD=keycloak_pass "$CONTAINER_NAME" psql -U keycloak -d keycloak -t -A -c "SELECT 1;" >/dev/null
echo "User 'keycloak' connection: OK"

docker exec -e PGPASSWORD=litellm_pass "$CONTAINER_NAME" psql -U litellm -d litellm -t -A -c "SELECT 1;" >/dev/null
echo "User 'litellm' connection: OK"

echo ""
echo "=== 4. Checking privilege separation for business services ==="
services="booking inventory payments risk notification ai finance"

for svc in $services; do
    echo "--- Service '$svc' ---"
    owner="${svc}_owner"
    app="${svc}_app"
    owner_pass="${svc}_owner_pass"
    app_pass="${svc}_app_pass"

    # A. Owner can CREATE TABLE
    docker exec -e PGPASSWORD="$owner_pass" "$CONTAINER_NAME" psql -U "$owner" -d "$svc" -c "CREATE TABLE IF NOT EXISTS test_privs (id int, val text);" >/dev/null
    echo "[$svc] Owner DDL (CREATE TABLE): OK"

    # B. App user can INSERT & SELECT
    docker exec -e PGPASSWORD="$app_pass" "$CONTAINER_NAME" psql -U "$app" -d "$svc" -c "INSERT INTO test_privs (id, val) VALUES (1, 'srh-test');" >/dev/null
    val=$(docker exec -e PGPASSWORD="$app_pass" "$CONTAINER_NAME" psql -U "$app" -d "$svc" -t -A -c "SELECT val FROM test_privs WHERE id = 1;")
    [ "$val" = "srh-test" ] || { echo "Error: [$svc] App user failed to SELECT inserted row"; exit 1; }
    echo "[$svc] App DML (INSERT & SELECT): OK"

    # C. App user CANNOT CREATE TABLE (must fail)
    if docker exec -e PGPASSWORD="$app_pass" "$CONTAINER_NAME" psql -U "$app" -d "$svc" -c "CREATE TABLE app_forbidden (id int);" 2>/dev/null; then
        echo "Error: [$svc] SECURITY VIOLATION: App user was able to CREATE TABLE!"
        exit 1
    fi
    echo "[$svc] App DDL forbidden (CREATE TABLE rejected): OK"

    # D. Debezium can SELECT from table
    deb_val=$(docker exec -e PGPASSWORD=debezium_pass "$CONTAINER_NAME" psql -U debezium -d "$svc" -t -A -c "SELECT val FROM test_privs WHERE id = 1;")
    [ "$deb_val" = "srh-test" ] || { echo "Error: [$svc] Debezium failed to SELECT from table"; exit 1; }
    echo "[$svc] Debezium read access: OK"

    # Cleanup
    docker exec -e PGPASSWORD="$owner_pass" "$CONTAINER_NAME" psql -U "$owner" -d "$svc" -c "DROP TABLE test_privs;" >/dev/null
    echo "[$svc] Owner cleanup (DROP TABLE): OK"
done

echo ""
echo ">>> ALL T1.1 CHECKS PASSED SUCCESSFULLY! <<<"
