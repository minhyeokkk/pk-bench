package bench.generator

import java.util.concurrent.atomic.AtomicLong

class SnowflakeGenerator(
    private val datacenterId: Long = 1L,
    private val workerId: Long = 1L,
) : PkGenerator<Long> {
    override val strategyName = "snowflake"

    // 2024-01-01 00:00:00 UTC
    private val epoch = 1704067200000L
    private val sequence = AtomicLong(0)
    private var lastTimestamp = -1L

    @Synchronized
    override fun generate(): Long {
        var timestamp = System.currentTimeMillis()
        if (timestamp == lastTimestamp) {
            val seq = sequence.incrementAndGet() and 0xFFF
            if (seq == 0L) {
                while (System.currentTimeMillis() <= lastTimestamp) { /* spin */ }
                timestamp = System.currentTimeMillis()
            }
        } else {
            sequence.set(0)
        }
        lastTimestamp = timestamp
        return ((timestamp - epoch) shl 22) or (datacenterId shl 17) or (workerId shl 12) or sequence.get()
    }
}
