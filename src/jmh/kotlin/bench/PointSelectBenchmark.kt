package bench

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/**
 * PK 단건 조회 성능 측정.
 *
 * 두 DB 모두 PK = B-tree 인덱스이므로 조회 자체는 O(log n)으로 유사하다.
 * 차이는 Buffer Pool / Shared Buffer 히트율에서 나온다:
 * - 순차 PK (AUTO_INCREMENT, Snowflake, UUID v7): 최근 삽입 데이터가 버퍼에 몰려있어 히트율 높음
 * - 랜덤 PK (UUID v4): 조회 대상이 흩어져 캐시 미스 증가
 *
 * @Threads(4): 동시 조회 시나리오도 포함
 */
@BenchmarkMode(Mode.Throughput, Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(4)
open class PointSelectBenchmark {

    @Benchmark
    fun selectBigint(state: BenchmarkState, bh: Blackhole) {
        bh.consume(state.repo.selectByBigintId(state.randomBigintId()))
    }

    @Benchmark
    fun selectUuidV4(state: BenchmarkState, bh: Blackhole) {
        bh.consume(state.repo.selectByBinaryId("t_uuid_v4", state.randomUuidV4()))
    }

    @Benchmark
    fun selectUuidV7(state: BenchmarkState, bh: Blackhole) {
        bh.consume(state.repo.selectByBinaryId("t_uuid_v7", state.randomUuidV7()))
    }

    @Benchmark
    fun selectUlid(state: BenchmarkState, bh: Blackhole) {
        bh.consume(state.repo.selectByBinaryId("t_ulid", state.randomUlid()))
    }

    @Benchmark
    fun selectSnowflake(state: BenchmarkState, bh: Blackhole) {
        bh.consume(state.repo.selectBySnowflakeId(state.randomSnowflake()))
    }
}
