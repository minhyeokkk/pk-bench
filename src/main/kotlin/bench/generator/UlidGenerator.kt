package bench.generator

import com.github.f4b6a3.ulid.UlidCreator

object UlidGenerator : PkGenerator<BinaryPk> {
    override val strategyName = "ulid"
    // Monotonic ULID: 동일 ms 내 여러 ID를 순서 보장하며 생성
    override fun generate(): BinaryPk = UlidCreator.getMonotonicUlid().toBytes()
}
