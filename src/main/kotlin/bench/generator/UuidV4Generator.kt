package bench.generator

import bench.util.uuidToBytes
import java.util.UUID

object UuidV4Generator : PkGenerator<BinaryPk> {
    override val strategyName = "uuid_v4"
    override fun generate(): BinaryPk = uuidToBytes(UUID.randomUUID())
}
