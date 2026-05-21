package bench.repository

import bench.config.DbDialect
import bench.util.bytesToUuid
import java.sql.Date
import java.sql.Statement
import java.sql.Types
import javax.sql.DataSource

class BenchRepository(
    private val dataSource: DataSource,
    private val dialect: DbDialect,
) {

    // ── INSERT ──────────────────────────────────────────────────────────────

    fun insertBigintAi(payload: String): Long {
        val sql = "INSERT INTO t_bigint_ai (payload) VALUES (?)"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
                ps.setString(1, payload)
                ps.executeUpdate()
                ps.generatedKeys.use { rs -> if (rs.next()) rs.getLong(1) else -1L }
            }
        }
    }

    fun insertBinary16(tableName: String, id: ByteArray, payload: String) {
        val sql = when (dialect) {
            DbDialect.MYSQL -> "INSERT INTO $tableName (id, payload) VALUES (?, ?)"
            // PostgreSQL UUID 타입은 문자열 또는 바이너리 모두 허용되나
            // setObject with UUID 타입이 가장 효율적임
            DbDialect.POSTGRESQL -> "INSERT INTO $tableName (id, payload) VALUES (?::uuid, ?)"
        }
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                when (dialect) {
                    DbDialect.MYSQL -> ps.setBytes(1, id)
                    DbDialect.POSTGRESQL -> ps.setObject(1, bytesToUuid(id))
                }
                ps.setString(2, payload)
                ps.executeUpdate()
            }
        }
    }

    fun insertSnowflake(id: Long, payload: String) {
        val sql = "INSERT INTO t_snowflake (id, payload) VALUES (?, ?)"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, id)
                ps.setString(2, payload)
                ps.executeUpdate()
            }
        }
    }

    fun insertComposite(createdDate: Date, payload: String): Long {
        val sql = when (dialect) {
            DbDialect.MYSQL -> "INSERT INTO t_composite (created_date, payload) VALUES (?, ?)"
            DbDialect.POSTGRESQL -> "INSERT INTO t_composite (created_date, payload) VALUES (?, ?) RETURNING seq_id"
        }
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
                ps.setDate(1, createdDate)
                ps.setString(2, payload)
                ps.executeUpdate()
                ps.generatedKeys.use { rs -> if (rs.next()) rs.getLong(1) else -1L }
            }
        }
    }

    // ── BULK INSERT ─────────────────────────────────────────────────────────

    fun bulkInsertBigintAi(payloads: List<String>) {
        val sql = "INSERT INTO t_bigint_ai (payload) VALUES (?)"
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            conn.prepareStatement(sql).use { ps ->
                payloads.forEach { payload ->
                    ps.setString(1, payload)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        }
    }

    fun bulkInsertBinary16(tableName: String, rows: List<Pair<ByteArray, String>>) {
        val sql = when (dialect) {
            DbDialect.MYSQL -> "INSERT INTO $tableName (id, payload) VALUES (?, ?)"
            DbDialect.POSTGRESQL -> "INSERT INTO $tableName (id, payload) VALUES (?::uuid, ?)"
        }
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            conn.prepareStatement(sql).use { ps ->
                rows.forEach { (id, payload) ->
                    when (dialect) {
                        DbDialect.MYSQL -> ps.setBytes(1, id)
                        DbDialect.POSTGRESQL -> ps.setObject(1, bytesToUuid(id))
                    }
                    ps.setString(2, payload)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        }
    }

    fun bulkInsertSnowflake(rows: List<Pair<Long, String>>) {
        val sql = "INSERT INTO t_snowflake (id, payload) VALUES (?, ?)"
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            conn.prepareStatement(sql).use { ps ->
                rows.forEach { (id, payload) ->
                    ps.setLong(1, id)
                    ps.setString(2, payload)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        }
    }

    fun bulkInsertComposite(rows: List<Pair<Date, String>>) {
        val sql = "INSERT INTO t_composite (created_date, payload) VALUES (?, ?)"
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            conn.prepareStatement(sql).use { ps ->
                rows.forEach { (date, payload) ->
                    ps.setDate(1, date)
                    ps.setString(2, payload)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        }
    }

    // ── POINT SELECT ────────────────────────────────────────────────────────

    fun selectByBigintId(id: Long): String? {
        val sql = "SELECT payload FROM t_bigint_ai WHERE id = ?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }
    }

    fun selectByBinaryId(tableName: String, id: ByteArray): String? {
        val sql = when (dialect) {
            DbDialect.MYSQL -> "SELECT payload FROM $tableName WHERE id = ?"
            DbDialect.POSTGRESQL -> "SELECT payload FROM $tableName WHERE id = ?::uuid"
        }
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                when (dialect) {
                    DbDialect.MYSQL -> ps.setBytes(1, id)
                    DbDialect.POSTGRESQL -> ps.setObject(1, bytesToUuid(id))
                }
                ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }
    }

    fun selectBySnowflakeId(id: Long): String? {
        val sql = "SELECT payload FROM t_snowflake WHERE id = ?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }
    }

    // ── RANGE SELECT ────────────────────────────────────────────────────────

    fun selectRecentByCreatedAt(tableName: String, limit: Int): List<String> {
        val sql = "SELECT payload FROM $tableName ORDER BY created_at DESC LIMIT ?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, limit)
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.getString(1)) }
                }
            }
        }
    }

    // created_at 인덱스 없이 PK 기반 최신 조회 (AUTO_INCREMENT/Snowflake 전용)
    fun selectRecentByPk(tableName: String, limit: Int): List<String> {
        val sql = "SELECT payload FROM $tableName ORDER BY id DESC LIMIT ?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, limit)
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.getString(1)) }
                }
            }
        }
    }

    // ── STORAGE STATS ───────────────────────────────────────────────────────

    fun getTableStats(tableName: String): TableStats = when (dialect) {
        DbDialect.MYSQL -> getMysqlTableStats(tableName)
        DbDialect.POSTGRESQL -> getPostgresTableStats(tableName)
    }

    private fun getMysqlTableStats(tableName: String): TableStats {
        dataSource.connection.use { it.createStatement().execute("ANALYZE TABLE $tableName") }
        val sql = """
            SELECT TABLE_ROWS, DATA_LENGTH, INDEX_LENGTH, DATA_FREE, AVG_ROW_LENGTH
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, tableName)
                ps.executeQuery().use { rs ->
                    if (rs.next()) TableStats(
                        tableName = tableName,
                        dialect = dialect,
                        tableRows = rs.getLong(1),
                        dataBytes = rs.getLong(2),
                        indexBytes = rs.getLong(3),
                        freeBytes = rs.getLong(4),
                        avgRowBytes = rs.getLong(5),
                    ) else TableStats(tableName = tableName, dialect = dialect)
                }
            }
        }
    }

    private fun getPostgresTableStats(tableName: String): TableStats {
        // pg_total_relation_size = 테이블 + 인덱스 + TOAST
        // pg_relation_size = 테이블 힙만
        val sql = """
            SELECT
                n_live_tup,
                pg_relation_size(quote_ident(relname))                          AS data_bytes,
                pg_total_relation_size(quote_ident(relname))
                    - pg_relation_size(quote_ident(relname))                    AS index_bytes,
                n_dead_tup * (pg_total_relation_size(quote_ident(relname))
                    / NULLIF(n_live_tup + n_dead_tup, 0))                      AS dead_bytes
            FROM pg_stat_user_tables
            WHERE relname = ?
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, tableName)
                ps.executeQuery().use { rs ->
                    if (rs.next()) TableStats(
                        tableName = tableName,
                        dialect = dialect,
                        tableRows = rs.getLong(1),
                        dataBytes = rs.getLong(2),
                        indexBytes = rs.getLong(3),
                        freeBytes = rs.getLong(4),
                    ) else TableStats(tableName = tableName, dialect = dialect)
                }
            }
        }
    }
}

data class TableStats(
    val tableName: String = "",
    val dialect: DbDialect = DbDialect.MYSQL,
    val tableRows: Long = 0,
    val dataBytes: Long = 0,
    val indexBytes: Long = 0,
    val freeBytes: Long = 0,
    val avgRowBytes: Long = 0,
) {
    val totalBytes: Long get() = dataBytes + indexBytes
    val fragmentationRatio: Double
        get() = if (dataBytes + freeBytes > 0) freeBytes.toDouble() / (dataBytes + freeBytes) else 0.0

    fun summary(): String = buildString {
        append("[$dialect/$tableName] ")
        append("rows=${tableRows} ")
        append("data=${dataBytes / 1024}KB ")
        append("index=${indexBytes / 1024}KB ")
        append("total=${totalBytes / 1024}KB ")
        append("frag=${"%.1f".format(fragmentationRatio * 100)}%")
    }
}
