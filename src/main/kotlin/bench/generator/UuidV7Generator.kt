package bench.generator

import bench.util.uuidToBytes
import com.github.f4b6a3.uuid.UuidCreator

object UuidV7Generator : PkGenerator<BinaryPk> {
    override val strategyName = "uuid_v7"
    // UuidCreator.getTimeOrderedEpoch() = UUID v7 (RFC 9562)
    // 상위 48비트 = Unix epoch ms → big-endian 저장 시 시간순 정렬 자동 보장
    override fun generate(): BinaryPk = uuidToBytes(UuidCreator.getTimeOrderedEpoch())
}
