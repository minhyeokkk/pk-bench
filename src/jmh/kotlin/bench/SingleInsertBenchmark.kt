package bench

import bench.generator.SnowflakeGenerator
import bench.generator.UlidGenerator
import bench.generator.UuidV4Generator
import bench.generator.UuidV7Generator
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.sql.Date
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 단건 INSERT 성능 측정.
 *
 * MySQL InnoDB vs PostgreSQL 힙 스토리지에서
 * PK 전략별 쓰기 성능 차이를 측정한다.
 *
 * 핵심 차이:
 * - MySQL: UUID v4 랜덤 → 클러스터드 인덱스 페이지 스플릿 → 느림
 * - PostgreSQL: 힙에 순서대로 append → PK 전략 영향이 MySQL보다 작음
 *               (단, B-tree 인덱스 자체의 랜덤 스플릿은 여전히 발생)
 */
@BenchmarkMode(Mode.Throughput, Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
open class SingleInsertBenchmark {

    @Benchmark
    fun bigintAutoIncrement(state: BenchmarkState, bh: Blackhole) {
        val id = state.repo.insertBigintAi("payload-${System.nanoTime()}")
        bh.consume(id)
    }

    @Benchmark
    fun uuidV4(state: BenchmarkState, bh: Blackhole) {
        val id = UuidV4Generator.generate()
        state.repo.insertBinary16("t_uuid_v4", id, "payload-${System.nanoTime()}")
        bh.consume(id)
    }

    @Benchmark
    fun uuidV7(state: BenchmarkState, bh: Blackhole) {
        val id = UuidV7Generator.generate()
        state.repo.insertBinary16("t_uuid_v7", id, "payload-${System.nanoTime()}")
        bh.consume(id)
    }

    @Benchmark
    fun ulid(state: BenchmarkState, bh: Blackhole) {
        val id = UlidGenerator.generate()
        state.repo.insertBinary16("t_ulid", id, "payload-${System.nanoTime()}")
        bh.consume(id)
    }

    @Benchmark
    fun snowflake(state: BenchmarkState, bh: Blackhole) {
        val id = state.snowflake.generate()
        state.repo.insertSnowflake(id, "payload-${System.nanoTime()}")
        bh.consume(id)
    }

    @Benchmark
    fun compositeKey(state: BenchmarkState, bh: Blackhole) {
        val id = state.repo.insertComposite(
            Date.valueOf(LocalDate.now()),
            "payload-${System.nanoTime()}"
        )
        bh.consume(id)
    }
}
