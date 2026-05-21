package bench

import bench.config.DataSourceConfig
import bench.config.DbDialect
import bench.generator.SnowflakeGenerator
import bench.generator.UlidGenerator
import bench.generator.UuidV4Generator
import bench.generator.UuidV7Generator
import bench.repository.BenchRepository
import bench.schema.SchemaInitializer
import com.zaxxer.hikari.HikariDataSource
import org.openjdk.jmh.annotations.*
import java.util.concurrent.ThreadLocalRandom

@State(Scope.Benchmark)
open class BenchmarkState {

    // JMH @Param: "mysql" 또는 "postgresql" — 벤치마크 실행 시 두 값 모두 실행됨
    @Param("mysql", "postgresql")
    open var db: String = "mysql"

    lateinit var repo: BenchRepository
    lateinit var dialect: DbDialect
    val snowflake = SnowflakeGenerator()

    // Point SELECT 벤치마크용 미리 삽입된 ID 샘플 풀
    val bigintIds = mutableListOf<Long>()
    val uuidV4Ids = mutableListOf<ByteArray>()
    val uuidV7Ids = mutableListOf<ByteArray>()
    val ulidIds = mutableListOf<ByteArray>()
    val snowflakeIds = mutableListOf<Long>()

    @Setup(Level.Trial)
    fun setup() {
        dialect = DbDialect.from(db)
        val ds = DataSourceConfig.create(dialect)
        repo = BenchRepository(ds, dialect)
        SchemaInitializer(ds, dialect).initialize()
        preload(5_000)
    }

    @TearDown(Level.Trial)
    fun teardown() {
        val ds = (repo as? Any)
        // HikariDataSource를 직접 닫으려면 DataSource 참조를 보관해야 함
        // 여기서는 JVM 종료 시 자동 정리에 위임
    }

    private fun preload(count: Int) {
        repeat(count) { i ->
            val p = "pre-$i"
            bigintIds += repo.insertBigintAi(p)
            UuidV4Generator.generate().also { id ->
                uuidV4Ids += id
                repo.insertBinary16("t_uuid_v4", id, p)
            }
            UuidV7Generator.generate().also { id ->
                uuidV7Ids += id
                repo.insertBinary16("t_uuid_v7", id, p)
            }
            UlidGenerator.generate().also { id ->
                ulidIds += id
                repo.insertBinary16("t_ulid", id, p)
            }
            snowflake.generate().also { id ->
                snowflakeIds += id
                repo.insertSnowflake(id, p)
            }
        }
    }

    fun randomBigintId(): Long = bigintIds[ThreadLocalRandom.current().nextInt(bigintIds.size)]
    fun randomUuidV4(): ByteArray = uuidV4Ids[ThreadLocalRandom.current().nextInt(uuidV4Ids.size)]
    fun randomUuidV7(): ByteArray = uuidV7Ids[ThreadLocalRandom.current().nextInt(uuidV7Ids.size)]
    fun randomUlid(): ByteArray = ulidIds[ThreadLocalRandom.current().nextInt(ulidIds.size)]
    fun randomSnowflake(): Long = snowflakeIds[ThreadLocalRandom.current().nextInt(snowflakeIds.size)]
}
