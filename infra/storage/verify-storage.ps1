# Verification script for T1.2 Redis and MinIO storage infrastructure

$ErrorActionPreference = "Stop"

Write-Host "=== 1. Checking Redis ===" -ForegroundColor Cyan

# Check ping
$redisPing = (docker exec srh-redis redis-cli ping).Trim()
Write-Host "Redis ping response: $redisPing (expected: PONG)"
if ($redisPing -ne "PONG") { throw "Redis did not respond with PONG!" }
Write-Host "Redis PING: OK" -ForegroundColor Green

# Check Redis read/write
docker exec srh-redis redis-cli set srh_test_key "srh_ok" | Out-Null
$redisVal = (docker exec srh-redis redis-cli get srh_test_key).Trim()
docker exec srh-redis redis-cli del srh_test_key | Out-Null

if ($redisVal -ne "srh_ok") { throw "Redis read/write test failed, got '$redisVal' instead of 'srh_ok'" }
Write-Host "Redis Read/Write test: OK" -ForegroundColor Green

Write-Host "`n=== 2. Checking MinIO Health ===" -ForegroundColor Cyan
$minioHealth = (docker exec srh-minio curl -s -o /dev/null -w "%{http_code}" http://localhost:9000/minio/health/live).Trim()
Write-Host "MinIO health status code: $minioHealth (expected: 200)"
if ($minioHealth -ne "200") { throw "MinIO health check failed with status $minioHealth" }
Write-Host "MinIO Live Healthcheck: OK" -ForegroundColor Green

Write-Host "`n=== 3. Checking MinIO Buckets ===" -ForegroundColor Cyan
$expectedBuckets = @("handover-photos", "acts", "catalog-media")

# List buckets using mc inside minio-init container (or temporary mc run)
$bucketsOutput = (docker run --rm --network srh-network minio/mc:latest /bin/sh -c "mc alias set srh http://minio:9000 minioadmin minioadmin >/dev/null 2>&1 && mc ls srh").Split("`r`n") | Where-Object { $_ -ne "" }

foreach ($bucket in $expectedBuckets) {
    $found = $false
    foreach ($line in $bucketsOutput) {
        if ($line -match "$bucket/?") {
            $found = $true
            break
        }
    }
    if ($found) {
        Write-Host "Bucket '$bucket': OK" -ForegroundColor Green
    } else {
        throw "Bucket '$bucket' not found in MinIO!"
    }
}

Write-Host "`n=== 4. Testing MinIO Upload and Download ===" -ForegroundColor Cyan
$testResult = (docker run --rm --network srh-network minio/mc:latest /bin/sh -c "
    mc alias set srh http://minio:9000 minioadmin minioadmin >/dev/null 2>&1
    echo 'srh-storage-test-content' > /tmp/test.txt
    mc cp /tmp/test.txt srh/catalog-media/test.txt >/dev/null 2>&1
    content=\$(mc cat srh/catalog-media/test.txt)
    mc rm srh/catalog-media/test.txt >/dev/null 2>&1
    echo \$content
").Trim()

if ($testResult -ne "srh-storage-test-content") {
    throw "MinIO object read/write test failed, got '$testResult'"
}
Write-Host "MinIO S3 Put/Get test: OK" -ForegroundColor Green

Write-Host "`n>>> ALL T1.2 CHECKS PASSED SUCCESSFULLY! <<<" -ForegroundColor Green
