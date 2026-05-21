package bench

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/**
 * 범위 조회 성능 측정.
 *
 * 여기서 MySQL vs PostgreSQL 차이가 가장 크게 드러난다:
 *
 * MySQL (클러스터드 인덱스):
 * - AUTO_INCREMENT/Snowflake PK 기반 ORDER BY id DESC: 클러스터드 인덱스를 직접 역순 스캔 → 최고 효율
 * - UUID v7/ULID ORDER BY created_at: secondary index → PK lookup (이중 접근) → 중간 효율
 * - UUID v4 ORDER BY created_at: secondary index에서 랜덤 PK로 이중 접근 → 최저 효율
 *
 * PostgreSQL (힙 스토리지):
 * - 모든 전략에서 created_at 인덱스 → 힙 lookup 패턴이 동일
 * - PK 타입 차이가 MySQL보다 훨씬 작음
 * - 단, UUID v4 인덱스 자체의 bloat(단편화)가 크면 인덱스 스캔 비용 증가
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
open class RangeSelectBenchmark {

    @Param("100", "1000")
    open var limit: Int = 100

    @Benchmark
    fun rangeByCreatedAt_bigint(state: BenchmarkState, bh: Blackhole) {
        bh.consume(state.repo.selectRecentByCreatedAt("t_bigint_ai", limit))
    }

    @Benchmark
    fun rangeByCreatedAt_uuidV4(state: BenchmarkState, bh: Blackhole) {
        bh.consume(state.repo.selectRecentByCreatedAt("t_uuid_v4", limit))
    }

    @Benchmark
    fun rangeByCreatedAt_uuidV7(state: BenchmarkState, bh: Blackhole) {
        bh.consume(state.repo.selectRecentByCreatedAt("t_uuid_v7", limit))
    }

    @Benchmark
    fun rangeByCreatedAt_ulid(state: BenchmarkState, bh: Blackhole) {
        bh.consume(state.repo.selectRecentByCreatedAt("t_ulid", limit))
    }

    @Benchmark
    fun rangeByCreatedAt_snowflake(state: BenchmarkState, bh: Blackhole) {
        bh.consume(state.repo.selectRecentByCreatedAt("t_snowflake", limit))
    }

    // 클러스터드 인덱스 직접 활용 (MySQL에서 유리)
    @Benchmark
    fun rangeByPk_bigint(state: BenchmarkState, bh: Blackhole) {
        bh.consume(state.repo.selectRecentByPk("t_bigint_ai", limit))
    }

    @Benchmark
    fun rangeByPk_snowflake(state: BenchmarkState, bh: Blackhole) {
        bh.consume(state.repo.selectRecentByPk("t_snowflake", limit))
    }
}
