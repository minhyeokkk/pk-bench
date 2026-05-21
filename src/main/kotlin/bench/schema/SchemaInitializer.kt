package bench.schema

import bench.config.DbDialect
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class SchemaInitializer(
    private val dataSource: DataSource,
    private val dialect: DbDialect,
) {
    private val log = LoggerFactory.getLogger(SchemaInitializer::class.java)

    fun initialize() {
        val statements = TableDefinitions.getDdlStatements(dialect)
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                statements.forEach { sql ->
                    conn.createStatement().use { it.execute(sql) }
                }
                conn.commit()
                log.info("[{}] Schema initialized ({} statements)", dialect, statements.size)
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    fun truncateAll() {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            when (dialect) {
                DbDialect.MYSQL -> conn.createStatement().execute("SET FOREIGN_KEY_CHECKS=0")
                DbDialect.POSTGRESQL -> {}
            }
            TableDefinitions.TABLE_NAMES.forEach { table ->
                conn.createStatement().use { it.execute("TRUNCATE TABLE $table") }
            }
            when (dialect) {
                DbDialect.MYSQL -> conn.createStatement().execute("SET FOREIGN_KEY_CHECKS=1")
                DbDialect.POSTGRESQL -> {}
            }
            conn.commit()
        }
    }
}
