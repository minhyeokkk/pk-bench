package bench.schema

import bench.config.DbDialect

object TableDefinitions {

    val TABLE_NAMES = listOf(
        "t_bigint_ai",
        "t_uuid_v4",
        "t_uuid_v7",
        "t_ulid",
        "t_snowflake",
        "t_composite",
    )

    fun getDdlStatements(dialect: DbDialect): List<String> = when (dialect) {
        DbDialect.MYSQL -> mysqlDdl()
        DbDialect.POSTGRESQL -> postgresqlDdl()
    }

    // ── MySQL InnoDB ─────────────────────────────────────────────────────────
    // UUID는 BINARY(16)으로 저장 (36바이트 CHAR(36) 대비 절반 크기)
    // InnoDB 클러스터드 인덱스: PK 순서가 곧 데이터 페이지 물리 순서
    private fun mysqlDdl(): List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS t_bigint_ai (
            id         BIGINT        NOT NULL AUTO_INCREMENT,
            payload    VARCHAR(255)  NOT NULL,
            created_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            extra_int  INT,
            extra_str  VARCHAR(100),
            PRIMARY KEY (id),
            INDEX idx_created_at (created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC
        """.trimIndent(),

        """
        CREATE TABLE IF NOT EXISTS t_uuid_v4 (
            id         BINARY(16)    NOT NULL,
            payload    VARCHAR(255)  NOT NULL,
            created_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            extra_int  INT,
            extra_str  VARCHAR(100),
            PRIMARY KEY (id),
            INDEX idx_created_at (created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC
        """.trimIndent(),

        """
        CREATE TABLE IF NOT EXISTS t_uuid_v7 (
            id         BINARY(16)    NOT NULL,
            payload    VARCHAR(255)  NOT NULL,
            created_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            extra_int  INT,
            extra_str  VARCHAR(100),
            PRIMARY KEY (id),
            INDEX idx_created_at (created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC
        """.trimIndent(),

        """
        CREATE TABLE IF NOT EXISTS t_ulid (
            id         BINARY(16)    NOT NULL,
            payload    VARCHAR(255)  NOT NULL,
            created_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            extra_int  INT,
            extra_str  VARCHAR(100),
            PRIMARY KEY (id),
            INDEX idx_created_at (created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC
        """.trimIndent(),

        """
        CREATE TABLE IF NOT EXISTS t_snowflake (
            id         BIGINT        NOT NULL,
            payload    VARCHAR(255)  NOT NULL,
            created_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            extra_int  INT,
            extra_str  VARCHAR(100),
            PRIMARY KEY (id),
            INDEX idx_created_at (created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC
        """.trimIndent(),

        // Composite Key: (created_date, seq_id)
        // MySQL InnoDB에서 AUTO_INCREMENT가 복합 PK의 두 번째 컬럼이면
        // 해당 컬럼 단독 인덱스 KEY(seq_id) 가 반드시 필요함
        """
        CREATE TABLE IF NOT EXISTS t_composite (
            created_date DATE          NOT NULL,
            seq_id       BIGINT        NOT NULL AUTO_INCREMENT,
            payload      VARCHAR(255)  NOT NULL,
            created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            extra_int    INT,
            extra_str    VARCHAR(100),
            PRIMARY KEY (created_date, seq_id),
            KEY (seq_id),
            INDEX idx_created_at (created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC
        """.trimIndent(),
    )

    // ── PostgreSQL ───────────────────────────────────────────────────────────
    // UUID는 네이티브 UUID 타입 사용 (16바이트 내부 저장, 비교 연산 최적화됨)
    // 힙 스토리지: 데이터 행은 삽입 순서대로 힙 페이지에 저장
    // PK 인덱스는 별도 B-tree (클러스터링 없음 → UUID v4 random 삽입 영향 다름)
    private fun postgresqlDdl(): List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS t_bigint_ai (
            id         BIGSERIAL     NOT NULL,
            payload    VARCHAR(255)  NOT NULL,
            created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
            extra_int  INT,
            extra_str  VARCHAR(100),
            PRIMARY KEY (id)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_bigint_ai_created_at ON t_bigint_ai (created_at)",

        """
        CREATE TABLE IF NOT EXISTS t_uuid_v4 (
            id         UUID          NOT NULL,
            payload    VARCHAR(255)  NOT NULL,
            created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
            extra_int  INT,
            extra_str  VARCHAR(100),
            PRIMARY KEY (id)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_uuid_v4_created_at ON t_uuid_v4 (created_at)",

        """
        CREATE TABLE IF NOT EXISTS t_uuid_v7 (
            id         UUID          NOT NULL,
            payload    VARCHAR(255)  NOT NULL,
            created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
            extra_int  INT,
            extra_str  VARCHAR(100),
            PRIMARY KEY (id)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_uuid_v7_created_at ON t_uuid_v7 (created_at)",

        """
        CREATE TABLE IF NOT EXISTS t_ulid (
            id         UUID          NOT NULL,
            payload    VARCHAR(255)  NOT NULL,
            created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
            extra_int  INT,
            extra_str  VARCHAR(100),
            PRIMARY KEY (id)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_ulid_created_at ON t_ulid (created_at)",

        """
        CREATE TABLE IF NOT EXISTS t_snowflake (
            id         BIGINT        NOT NULL,
            payload    VARCHAR(255)  NOT NULL,
            created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
            extra_int  INT,
            extra_str  VARCHAR(100),
            PRIMARY KEY (id)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_snowflake_created_at ON t_snowflake (created_at)",

        """
        CREATE TABLE IF NOT EXISTS t_composite (
            created_date DATE         NOT NULL,
            seq_id       BIGINT       GENERATED ALWAYS AS IDENTITY,
            payload      VARCHAR(255) NOT NULL,
            created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
            extra_int    INT,
            extra_str    VARCHAR(100),
            PRIMARY KEY (created_date, seq_id)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_composite_created_at ON t_composite (created_at)",
    )
}
