package bench

import bench.generator.UuidV4Generator
import bench.generator.UuidV7Generator
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/**
 * MongoDB WiredTiger vs MySQL InnoDB INSERT 성능 비교.
 *
 * WiredTiger 핵심:
 * - _id = 클러스터드 B-tree 인덱스 (MySQL InnoDB와 동일한 구조)
 * - ObjectId (12바이트, 시간 정렬) → 순차 삽입 → 스플릿 최소
 * - UUID v4 (랜덤) → B-tree 중간 삽입 → 스플릿 발생
 *
 * MySQL과의 차이:
 * - MongoDB는 document 단위 잠금 (MySQL: row 단위)
 * - MongoDB는 journaling (WAL과 유사)
 * - MongoDB insertMany ordered=false → 병렬 삽입 가능
 */
@BenchmarkMode(Mode.Throughput, Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
open class MongoSingleInsertBenchmark {

    @Benchmark
    fun objectId(state: MongoBenchmarkState, bh: Blackhole) {
        bh.consume(state.mongo.insertObjectId("payload-${System.nanoTime()}"))
    }

    @Benchmark
    fun uuidV4(state: MongoBenchmarkState, bh: Blackhole) {
        val id = UuidV4Generator.generate()
        state.mongo.insertUuidV4(id, "payload-${System.nanoTime()}")
        bh.consume(id)
    }

    @Benchmark
    fun uuidV7(state: MongoBenchmarkState, bh: Blackhole) {
        val id = UuidV7Generator.generate()
        state.mongo.insertUuidV7(id, "payload-${System.nanoTime()}")
        bh.consume(id)
    }

    @Benchmark
    fun snowflake(state: MongoBenchmarkState, bh: Blackhole) {
        val id = state.snowflake.generate()
        state.mongo.insertSnowflake(id, "payload-${System.nanoTime()}")
        bh.consume(id)
    }
}

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 15, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
open class MongoBulkInsertBenchmark {

    companion object {
        const val BATCH_SIZE = 1000
    }

    @Benchmark
    fun bulkObjectId(state: MongoBenchmarkState, bh: Blackhole) {
        val payloads = List(BATCH_SIZE) { "bulk-$it-${System.nanoTime()}" }
        state.mongo.bulkInsertObjectId(payloads)
        bh.consume(payloads.size)
    }

    @Benchmark
    fun bulkUuidV4(state: MongoBenchmarkState, bh: Blackhole) {
        val rows = List(BATCH_SIZE) { UuidV4Generator.generate() to "bulk-$it" }
        state.mongo.bulkInsertUuidV4(rows)
        bh.consume(rows.size)
    }

    @Benchmark
    fun bulkUuidV7(state: MongoBenchmarkState, bh: Blackhole) {
        val rows = List(BATCH_SIZE) { UuidV7Generator.generate() to "bulk-$it" }
        state.mongo.bulkInsertUuidV7(rows)
        bh.consume(rows.size)
    }

    @Benchmark
    fun bulkSnowflake(state: MongoBenchmarkState, bh: Blackhole) {
        val rows = List(BATCH_SIZE) { state.snowflake.generate() to "bulk-$it" }
        state.mongo.bulkInsertSnowflake(rows)
        bh.consume(rows.size)
    }
}
