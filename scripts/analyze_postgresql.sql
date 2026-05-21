-- ============================================================
-- PostgreSQL: PK 전략별 스토리지 및 인덱스 bloat 분석
-- ============================================================

-- 1. 테이블별 스토리지 크기 비교
SELECT
    relname                                             AS tbl,
    n_live_tup                                          AS rows_est,
    ROUND(pg_relation_size(quote_ident(relname)) / 1024.0 / 1024, 2) AS data_mb,
    ROUND((pg_total_relation_size(quote_ident(relname))
           - pg_relation_size(quote_ident(relname))) / 1024.0 / 1024, 2) AS index_mb,
    ROUND(pg_total_relation_size(quote_ident(relname)) / 1024.0 / 1024, 2) AS total_mb,
    n_dead_tup                                          AS dead_tuples,
    ROUND(n_dead_tup::numeric / NULLIF(n_live_tup + n_dead_tup, 0) * 100, 2) AS dead_pct
FROM pg_stat_user_tables
WHERE relname IN ('t_bigint_ai','t_uuid_v4','t_uuid_v7','t_ulid','t_snowflake','t_composite')
ORDER BY total_mb DESC;

-- 2. 인덱스별 크기 및 bloat 추정
-- PostgreSQL에서 UUID v4 PK 인덱스는 랜덤 삽입으로 인해 B-tree 페이지 fill factor가 낮아짐
SELECT
    i.relname                                           AS tbl,
    ix.relname                                          AS idx,
    ix.relkind,
    ROUND(pg_relation_size(ix.oid) / 1024.0 / 1024, 2) AS index_mb,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes si
JOIN pg_class i ON i.oid = si.relid
JOIN pg_class ix ON ix.oid = si.indexrelid
WHERE i.relname IN ('t_bigint_ai','t_uuid_v4','t_uuid_v7','t_ulid','t_snowflake','t_composite')
ORDER BY i.relname, index_mb DESC;

-- 3. pgstattuple을 사용한 B-tree 단편화 측정 (extension 설치 필요)
-- CREATE EXTENSION IF NOT EXISTS pgstattuple;
-- SELECT * FROM pgstattuple('t_uuid_v4');
-- SELECT * FROM pgstatindex('t_uuid_v4_pkey');

-- 4. 버퍼 캐시 히트율 (shared_buffers 효율)
SELECT
    relname,
    heap_blks_read,
    heap_blks_hit,
    ROUND(heap_blks_hit::numeric / NULLIF(heap_blks_hit + heap_blks_read, 0) * 100, 2) AS heap_hit_pct,
    idx_blks_read,
    idx_blks_hit,
    ROUND(idx_blks_hit::numeric / NULLIF(idx_blks_hit + idx_blks_read, 0) * 100, 2) AS idx_hit_pct
FROM pg_statio_user_tables
WHERE relname IN ('t_bigint_ai','t_uuid_v4','t_uuid_v7','t_ulid','t_snowflake','t_composite')
ORDER BY relname;

-- 5. 인덱스 B-tree 페이지 밀도 추정
-- 페이지 수 / 행 수 비율 → 낮을수록 인덱스 밀도가 높아 효율적
SELECT
    i.relname                                               AS tbl,
    ix.relname                                              AS idx,
    s.n_live_tup,
    pg_relation_size(ix.oid) / 8192                        AS index_pages,
    ROUND(s.n_live_tup::numeric
          / NULLIF(pg_relation_size(ix.oid) / 8192, 0), 0) AS rows_per_page
FROM pg_stat_user_indexes si
JOIN pg_class i ON i.oid = si.relid
JOIN pg_class ix ON ix.oid = si.indexrelid
JOIN pg_stat_user_tables s ON s.relname = i.relname
WHERE i.relname IN ('t_bigint_ai','t_uuid_v4','t_uuid_v7','t_ulid','t_snowflake','t_composite')
  AND ix.relname LIKE '%_pkey'
ORDER BY i.relname;

-- 6. Secondary Index 크기 비교
-- PostgreSQL UUID vs BIGINT Secondary Index 크기 배율
WITH idx_sizes AS (
    SELECT
        i.relname   AS tbl,
        ix.relname  AS idx,
        pg_relation_size(ix.oid) AS size_bytes
    FROM pg_stat_user_indexes si
    JOIN pg_class i  ON i.oid = si.relid
    JOIN pg_class ix ON ix.oid = si.indexrelid
    WHERE i.relname IN ('t_bigint_ai','t_uuid_v4','t_uuid_v7','t_ulid','t_snowflake','t_composite')
      AND ix.relname NOT LIKE '%_pkey'
)
SELECT
    tbl,
    idx,
    ROUND(size_bytes / 1024.0 / 1024, 2) AS index_mb,
    ROUND(size_bytes::numeric / NULLIF(
        (SELECT size_bytes FROM idx_sizes WHERE tbl = 't_bigint_ai' LIMIT 1), 0
    ), 2) AS ratio_vs_bigint
FROM idx_sizes
ORDER BY tbl;
