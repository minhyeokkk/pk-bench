#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "=== PK Bench Setup ==="

echo "[1/4] Starting MySQL + PostgreSQL containers..."
docker compose -f "$PROJECT_ROOT/docker-compose.yml" up -d

echo "[2/4] Waiting for MySQL..."
until docker exec pk-bench-mysql mysqladmin ping -h localhost -u bench -pbench123 --silent 2>/dev/null; do
    printf "."
    sleep 2
done
echo " OK"

echo "[3/4] Waiting for PostgreSQL..."
until docker exec pk-bench-postgres pg_isready -U bench -d pk_bench -q 2>/dev/null; do
    printf "."
    sleep 2
done
echo " OK"

echo "[4/4] Building JMH jar..."
cd "$PROJECT_ROOT"
./gradlew jmhJar --no-daemon -q

echo ""
echo "=== Setup Complete ==="
echo "Run all benchmarks:   ./scripts/run-bench.sh"
echo "Run specific class:   ./scripts/run-bench.sh SingleInsert"
echo "Run single DB only:   ./scripts/run-bench.sh '.*' mysql"
