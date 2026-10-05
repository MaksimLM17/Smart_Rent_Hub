#!/bin/sh
set -e

echo "=== 1. Checking Redis ==="
redis_ping=$(docker exec srh-redis redis-cli ping)
echo "Redis ping response: $redis_ping (expected: PONG)"
[ "$redis_ping" = "PONG" ] || { echo "Error: Redis ping failed"; exit 1; }
echo "Redis PING: OK"

docker exec srh-redis redis-cli set srh_test_key "srh_ok" >/dev/null
redis_val=$(docker exec srh-redis redis-cli get srh_test_key)
docker exec srh-redis redis-cli del srh_test_key >/dev/null
[ "$redis_val" = "srh_ok" ] || { echo "Error: Redis read/write failed"; exit 1; }
echo "Redis Read/Write test: OK"

echo ""
echo "=== 2. Checking MinIO Health ==="
minio_health=$(docker exec srh-minio curl -s -o /dev/null -w "%{http_code}" http://localhost:9000/minio/health/live)
echo "MinIO health status code: $minio_health (expected: 200)"
[ "$minio_health" = "200" ] || { echo "Error: MinIO health check failed"; exit 1; }
echo "MinIO Live Healthcheck: OK"

echo ""
echo "=== 3. Checking MinIO Buckets ==="
expected_buckets="handover-photos acts catalog-media"
buckets_output=$(docker run --rm --network srh-network minio/mc:latest /bin/sh -c "mc alias set srh http://minio:9000 minioadmin minioadmin >/dev/null 2>&1 && mc ls srh")

for bucket in $expected_buckets; do
    if echo "$buckets_output" | grep -q "$bucket"; then
        echo "Bucket '$bucket': OK"
    else
        echo "Error: Bucket '$bucket' not found!"
        exit 1
    fi
done

echo ""
echo "=== 4. Testing MinIO Upload and Download ==="
test_result=$(docker run --rm --network srh-network minio/mc:latest /bin/sh -c "
    mc alias set srh http://minio:9000 minioadmin minioadmin >/dev/null 2>&1
    echo 'srh-storage-test-content' > /tmp/test.txt
    mc cp /tmp/test.txt srh/catalog-media/test.txt >/dev/null 2>&1
    content=\$(mc cat srh/catalog-media/test.txt)
    mc rm srh/catalog-media/test.txt >/dev/null 2>&1
    echo \$content
")

[ "$test_result" = "srh-storage-test-content" ] || { echo "Error: MinIO object read/write failed"; exit 1; }
echo "MinIO S3 Put/Get test: OK"

echo ""
echo ">>> ALL T1.2 CHECKS PASSED SUCCESSFULLY! <<<"
