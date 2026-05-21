plugins {
    kotlin("jvm") version "2.1.10"
    id("me.champeau.jmh") version "0.7.2"
}

group = "bench"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xjvm-default=all")
        javaParameters = true
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // JDBC Drivers
    implementation("com.mysql:mysql-connector-j:9.2.0")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:6.2.1")

    // UUID v7
    implementation("com.github.f4b6a3:uuid-creator:6.0.0")
    // ULID
    implementation("com.github.f4b6a3:ulid-creator:5.2.3")

    // MongoDB
    implementation("org.mongodb:mongodb-driver-sync:5.2.1")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("org.slf4j:slf4j-api:2.0.17")

    // JMH
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

jmh {
    warmupIterations = 3
    warmup = "5s"
    iterations = 5
    timeOnIteration = "10s"
    fork = 1
    threads = 1
    benchmarkMode = listOf("thrpt", "avgt")
    timeUnit = "ms"
    resultFormat = "JSON"
    resultsFile = project.file("${project.layout.buildDirectory.get()}/reports/jmh/results.json")
    jvmArgsAppend = listOf(
        "-Xmx2g",
        "-XX:+UseG1GC",
        "-Dbench.mysql.url=jdbc:mysql://localhost:3306/pk_bench?rewriteBatchedStatements=true&cachePrepStmts=true&useServerPrepStmts=true",
        "-Dbench.mysql.user=bench",
        "-Dbench.mysql.password=bench123",
        "-Dbench.pg.url=jdbc:postgresql://localhost:5432/pk_bench?reWriteBatchedInserts=true",
        "-Dbench.pg.user=bench",
        "-Dbench.pg.password=bench123",
        "-Dbench.mongo.uri=mongodb://bench:bench123@localhost:27017/pk_bench?authSource=admin"
    )
}

tasks.test {
    useJUnitPlatform()
}
