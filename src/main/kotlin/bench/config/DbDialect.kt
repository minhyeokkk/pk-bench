package bench.config

enum class DbDialect(
    val urlProperty: String,
    val userProperty: String,
    val passwordProperty: String,
    val defaultUrl: String,
    val defaultUser: String,
    val defaultPassword: String,
) {
    MYSQL(
        urlProperty = "bench.mysql.url",
        userProperty = "bench.mysql.user",
        passwordProperty = "bench.mysql.password",
        defaultUrl = "jdbc:mysql://localhost:3306/pk_bench?rewriteBatchedStatements=true&cachePrepStmts=true&useServerPrepStmts=true",
        defaultUser = "bench",
        defaultPassword = "bench123",
    ),
    POSTGRESQL(
        urlProperty = "bench.pg.url",
        userProperty = "bench.pg.user",
        passwordProperty = "bench.pg.password",
        defaultUrl = "jdbc:postgresql://localhost:5432/pk_bench?reWriteBatchedInserts=true",
        defaultUser = "bench",
        defaultPassword = "bench123",
    );

    val url: String get() = System.getProperty(urlProperty, defaultUrl)
    val user: String get() = System.getProperty(userProperty, defaultUser)
    val password: String get() = System.getProperty(passwordProperty, defaultPassword)

    companion object {
        fun from(name: String): DbDialect = when (name.lowercase()) {
            "mysql" -> MYSQL
            "postgresql", "postgres", "pg" -> POSTGRESQL
            else -> error("Unknown dialect: $name. Use 'mysql' or 'postgresql'")
        }
    }
}
