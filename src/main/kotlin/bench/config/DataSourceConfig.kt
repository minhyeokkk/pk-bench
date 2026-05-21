package bench.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

object DataSourceConfig {

    fun create(dialect: DbDialect, poolSize: Int = 10): DataSource {
        val config = HikariConfig().apply {
            jdbcUrl = dialect.url
            username = dialect.user
            password = dialect.password
            maximumPoolSize = poolSize
            minimumIdle = minOf(5, poolSize)
            connectionTimeout = 30_000
            idleTimeout = 600_000
            maxLifetime = 1_800_000

            when (dialect) {
                DbDialect.MYSQL -> {
                    driverClassName = "com.mysql.cj.jdbc.Driver"
                    connectionInitSql = "SET SESSION time_zone='+00:00'"
                }
                DbDialect.POSTGRESQL -> {
                    driverClassName = "org.postgresql.Driver"
                    connectionInitSql = "SET TIME ZONE 'UTC'"
                }
            }
        }
        return HikariDataSource(config)
    }
}
