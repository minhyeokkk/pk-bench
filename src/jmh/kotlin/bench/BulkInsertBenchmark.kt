package bench

import bench.generator.UlidGenerator
import bench.generator.UuidV4Generator
import bench.generator.UuidV7Generator
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.sql.Date
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 배치 INSERT 성능 측정 (1000건 단위).
 *
 * MySQL: rewriteBatchedStatements=true → multi-row INSERT로 재작성
 * PostgreSQL: reWriteBatchedInserts=true → JDBC 레벨 배치 최적화
 *
 * UUID v4는 배치에서도 랜덤 순서로 삽입되므로
 * MySQL 클러스터드 인덱스에서 가장 불리하다.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 15, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
open class BulkInsertBenchmark {

    companion object {
        const val BATCH_SIZE = 1000
    }

    @Benchmark
    fun bulkBigintAi(state: BenchmarkState, bh: Blackhole) {
        val payloads = List(BATCH_SIZE) { "bulk-$it-${System.nanoTime()}" }
        state.repo.bulkInsertBigintAi(payloads)
        bh.consume(payloads.size)
    }

    @Benchmark
    fun bulkUuidV4(state: BenchmarkState, bh: Blackhole) {
        val rows = List(BATCH_SIZE) { UuidV4Generator.generate() to "bulk-$it" }
        state.repo.bulkInsertBinary16("t_uuid_v4", rows)
        bh.consume(rows.size)
    }

    @Benchmark
    fun bulkUuidV7(state: BenchmarkState, bh: Blackhole) {
        val rows = List(BATCH_SIZE) { UuidV7Generator.generate() to "bulk-$it" }
        state.repo.bulkInsertBinary16("t_uuid_v7", rows)
        bh.consume(rows.size)
    }

    @Benchmark
    fun bulkUlid(state: BenchmarkState, bh: Blackhole) {
        val rows = List(BATCH_SIZE) { UlidGenerator.generate() to "bulk-$it" }
        state.repo.bulkInsertBinary16("t_ulid", rows)
        bh.consume(rows.size)
    }

    @Benchmark
    fun bulkSnowflake(state: BenchmarkState, bh: Blackhole) {
        val rows = List(BATCH_SIZE) { state.snowflake.generate() to "bulk-$it" }
        state.repo.bulkInsertSnowflake(rows)
        bh.consume(rows.size)
    }

    @Benchmark
    fun bulkComposite(state: BenchmarkState, bh: Blackhole) {
        val today = Date.valueOf(LocalDate.now())
        val rows = List(BATCH_SIZE) { today to "bulk-$it" }
        state.repo.bulkInsertComposite(rows)
        bh.consume(rows.size)
    }
}
