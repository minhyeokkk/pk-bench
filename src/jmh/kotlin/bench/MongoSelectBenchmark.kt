package bench

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/**
 * MongoDB 조회 성능 벤치마크.
 *
 * Point SELECT:
 * - _id 기반 조회 → WiredTiger B-tree 탐색
 * - MySQL과 동일하게 O(log n)
 * - ObjectId/Snowflake (순차) vs UUID v4 (랜덤) → Buffer Cache 히트율 차이
 *
 * Range SELECT:
 * - created_at 인덱스 → document 조회 (MySQL Secondary Index + 클러스터드 이중 접근과 유사)
 * - MongoDB는 document 전체를 힙 페이지에서 가져옴 (PostgreSQL 힙 구조와 유사)
 */
@BenchmarkMode(Mode.Throughput, Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(4)
open class MongoPointSelectBenchmark {

    @Benchmark
    fun selectByObjectId(state: MongoBenchmarkState, bh: Blackhole) {
        bh.consume(state.mongo.selectByObjectId(state.randomObjectId()))
    }

    @Benchmark
    fun selectByUuidV4(state: MongoBenchmarkState, bh: Blackhole) {
        bh.consume(state.mongo.selectByUuidV4(state.randomUuidV4()))
    }

    @Benchmark
    fun selectByUuidV7(state: MongoBenchmarkState, bh: Blackhole) {
        bh.consume(state.mongo.selectByUuidV7(state.randomUuidV7()))
    }

    @Benchmark
    fun selectBySnowflake(state: MongoBenchmarkState, bh: Blackhole) {
        bh.consume(state.mongo.selectBySnowflake(state.randomSnowflake()))
    }
}

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
open class MongoRangeSelectBenchmark {

    @Param("100", "1000")
    open var limit: Int = 100

    @Benchmark
    fun rangeObjectId(state: MongoBenchmarkState, bh: Blackhole) {
        bh.consume(state.mongo.selectRecentByCreatedAt(state.mongo.colObjectId, limit))
    }

    @Benchmark
    fun rangeUuidV4(state: MongoBenchmarkState, bh: Blackhole) {
        bh.consume(state.mongo.selectRecentByCreatedAt(state.mongo.colUuidV4, limit))
    }

    @Benchmark
    fun rangeUuidV7(state: MongoBenchmarkState, bh: Blackhole) {
        bh.consume(state.mongo.selectRecentByCreatedAt(state.mongo.colUuidV7, limit))
    }

    @Benchmark
    fun rangeSnowflake(state: MongoBenchmarkState, bh: Blackhole) {
        bh.consume(state.mongo.selectRecentByCreatedAt(state.mongo.colSnowflake, limit))
    }
}
