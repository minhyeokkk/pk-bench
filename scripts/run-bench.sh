#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESULTS_DIR="$PROJECT_ROOT/results/$(date +%Y%m%d_%H%M%S)"
mkdir -p "$RESULTS_DIR"

FILTER="${1:-.*}"
DB_PARAM="${2:-}"  # 비어있으면 mysql + postgresql 둘 다 실행

echo "=== PK Benchmark: filter=$FILTER db=${DB_PARAM:-both} ==="

JVM_ARGS=(
    "-Xmx2g"
    "-XX:+UseG1GC"
    "-Dbench.mysql.url=jdbc:mysql://localhost:3306/pk_bench?rewriteBatchedStatements=true&cachePrepStmts=true&useServerPrepStmts=true"
    "-Dbench.mysql.user=bench"
    "-Dbench.mysql.password=bench123"
    "-Dbench.pg.url=jdbc:postgresql://localhost:5432/pk_bench?reWriteBatchedInserts=true"
    "-Dbench.pg.user=bench"
    "-Dbench.pg.password=bench123"
)

PARAM_ARGS=()
if [[ -n "$DB_PARAM" ]]; then
    PARAM_ARGS=("-p" "db=$DB_PARAM")
fi

java "${JVM_ARGS[@]}" \
    -jar "$PROJECT_ROOT/build/libs/pk-bench-jmh.jar" \
    -f 1 -wi 3 -w 5s -i 5 -r 10s \
    -rf json -rff "$RESULTS_DIR/results.json" \
    "${PARAM_ARGS[@]}" \
    "$FILTER"

echo ""
echo "=== Benchmark Complete ==="
echo "Results: $RESULTS_DIR/results.json"

# DB 스토리지 분석 스냅샷
echo "[Post-bench] Collecting storage stats..."

docker exec pk-bench-mysql mysql -u bench -pbench123 pk_bench \
    < "$PROJECT_ROOT/scripts/analyze_mysql.sql" \
    > "$RESULTS_DIR/mysql_storage.txt" 2>&1 || true

docker exec pk-bench-postgres psql -U bench -d pk_bench \
    -f /dev/stdin < "$PROJECT_ROOT/scripts/analyze_postgresql.sql" \
    > "$RESULTS_DIR/pg_storage.txt" 2>&1 || true

echo "MySQL storage:      $RESULTS_DIR/mysql_storage.txt"
echo "PostgreSQL storage: $RESULTS_DIR/pg_storage.txt"
