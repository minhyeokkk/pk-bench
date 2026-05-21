package bench.generator

import bench.util.bytesToUuid
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GeneratorTest {

    @Test
    fun `UuidV4Generator returns 16-byte arrays`() {
        val id = UuidV4Generator.generate()
        assertEquals(16, id.size)
    }

    @Test
    fun `UuidV7Generator returns 16-byte arrays that are time-ordered`() {
        val ids = (1..100).map { UuidV7Generator.generate() }
        val uuids = ids.map { bytesToUuid(it) }
        val sorted = uuids.sortedWith(compareBy({ it.mostSignificantBits }, { it.leastSignificantBits }))
        assertEquals(uuids, sorted, "UUID v7 IDs should be generated in ascending order")
    }

    @Test
    fun `UlidGenerator returns 16-byte arrays that are monotonically increasing`() {
        val ids = (1..100).map { UlidGenerator.generate() }
        for (i in 1 until ids.size) {
            val prev = ids[i - 1]
            val curr = ids[i]
            assertTrue(
                compareByteArrays(prev, curr) <= 0,
                "ULID[$i] should be >= ULID[${i-1}]"
            )
        }
    }

    @Test
    fun `SnowflakeGenerator returns unique monotonically increasing values`() {
        val gen = SnowflakeGenerator()
        val ids = (1..1000).map { gen.generate() }
        assertEquals(ids.size, ids.toSet().size, "Snowflake IDs must be unique")
        val sorted = ids.sorted()
        assertEquals(ids, sorted, "Snowflake IDs must be monotonically increasing")
    }

    private fun compareByteArrays(a: ByteArray, b: ByteArray): Int {
        for (i in a.indices) {
            val cmp = (a[i].toInt() and 0xFF).compareTo(b[i].toInt() and 0xFF)
            if (cmp != 0) return cmp
        }
        return 0
    }
}
