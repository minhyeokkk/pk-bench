package bench.util

import java.nio.ByteBuffer
import java.util.UUID

fun uuidToBytes(uuid: UUID): ByteArray {
    val bb = ByteBuffer.allocate(16)
    bb.putLong(uuid.mostSignificantBits)
    bb.putLong(uuid.leastSignificantBits)
    return bb.array()
}

fun bytesToUuid(bytes: ByteArray): UUID {
    val bb = ByteBuffer.wrap(bytes)
    return UUID(bb.long, bb.long)
}
