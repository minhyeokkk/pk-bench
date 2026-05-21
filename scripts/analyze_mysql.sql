-- ============================================================
-- MySQL InnoDB: PK 전략별 스토리지 및 단편화 분석
-- ============================================================

-- 1. 테이블별 스토리지 크기 비교
SELECT
    TABLE_NAME                                                          AS tbl,
    TABLE_ROWS                                                          AS rows_est,
    ROUND(DATA_LENGTH   / 1024 / 1024, 2)                              AS data_mb,
    ROUND(INDEX_LENGTH  / 1024 / 1024, 2)                              AS index_mb,
    ROUND((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024, 2)               AS total_mb,
    ROUND(DATA_FREE     / 1024 / 1024, 2)                              AS free_mb,
    ROUND(DATA_FREE / NULLIF(DATA_LENGTH + DATA_FREE, 0) * 100, 2)     AS frag_pct,
    AVG_ROW_LENGTH                                                      AS avg_row_bytes
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('t_bigint_ai','t_uuid_v4','t_uuid_v7','t_ulid','t_snowflake','t_composite')
ORDER BY total_mb DESC;

-- 2. InnoDB 내부 통계: 페이지 스플릿 및 쓰기 증폭
SELECT VARIABLE_NAME, VARIABLE_VALUE
FROM performance_schema.global_status
WHERE VARIABLE_NAME IN (
    'Innodb_pages_written',       -- 총 쓰기 페이지
    'Innodb_data_written',        -- 총 쓰기 바이트
    'Innodb_log_writes',          -- redo log 쓰기 횟수
    'Innodb_rows_inserted',       -- 총 삽입 행 수
    'Innodb_buffer_pool_reads',   -- 버퍼풀 미스 (디스크 읽기)
    'Innodb_buffer_pool_read_requests'  -- 버퍼풀 전체 읽기 요청
)
ORDER BY VARIABLE_NAME;

-- 3. 버퍼풀 히트율 (높을수록 캐시 효율 좋음)
SELECT
    ROUND(
        (1 - s1.VARIABLE_VALUE / NULLIF(s2.VARIABLE_VALUE, 0)) * 100,
        2
    ) AS buffer_pool_hit_pct
FROM
    performance_schema.global_status s1,
    performance_schema.global_status s2
WHERE s1.VARIABLE_NAME = 'Innodb_buffer_pool_reads'
  AND s2.VARIABLE_NAME = 'Innodb_buffer_pool_read_requests';

-- 4. 인덱스별 크기 (Secondary Index에 PK 크기가 포함됨을 확인)
-- BIGINT PK의 Secondary Index < UUID PK의 Secondary Index
SELECT
    s.TABLE_NAME                                            AS tbl,
    s.INDEX_NAME                                            AS idx,
    ROUND(s.STAT_VALUE * @@innodb_page_size / 1024 / 1024, 2) AS index_mb
FROM mysql.innodb_index_stats s
WHERE s.database_name = DATABASE()
  AND s.stat_name = 'size'
  AND s.TABLE_NAME IN ('t_bigint_ai','t_uuid_v4','t_uuid_v7','t_ulid','t_snowflake','t_composite')
ORDER BY s.TABLE_NAME, index_mb DESC;

-- 5. PK 타입별 Secondary Index 크기 배율 비교
-- UUID(16바이트) Secondary Index 크기 / BIGINT(8바이트) Secondary Index 크기
WITH idx_sizes AS (
    SELECT
        s.TABLE_NAME,
        s.INDEX_NAME,
        s.STAT_VALUE * @@innodb_page_size AS size_bytes
    FROM mysql.innodb_index_stats s
    WHERE s.database_name = DATABASE()
      AND s.stat_name = 'size'
      AND s.INDEX_NAME != 'PRIMARY'
      AND s.TABLE_NAME IN ('t_bigint_ai','t_uuid_v4','t_uuid_v7','t_ulid','t_snowflake','t_composite')
)
SELECT
    i.TABLE_NAME,
    i.INDEX_NAME,
    ROUND(i.size_bytes / 1024 / 1024, 2) AS index_mb,
    ROUND(i.size_bytes / NULLIF(base.size_bytes, 0), 2) AS ratio_vs_bigint
FROM idx_sizes i
CROSS JOIN (
    SELECT size_bytes FROM idx_sizes WHERE TABLE_NAME = 't_bigint_ai' AND INDEX_NAME = 'idx_created_at'
) base
ORDER BY i.TABLE_NAME;
