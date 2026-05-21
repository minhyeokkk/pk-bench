package bench

import bench.generator.SnowflakeGenerator
import bench.generator.UuidV4Generator
import bench.generator.UuidV7Generator
import bench.repository.MongoRepository
import org.bson.types.ObjectId
import org.openjdk.jmh.annotations.*
import java.util.concurrent.ThreadLocalRandom

@State(Scope.Benchmark)
open class MongoBenchmarkState {

    lateinit var mongo: MongoRepository
    val snowflake = SnowflakeGenerator(datacenterId = 2L, workerId = 1L)

    val insertedObjectIds = mutableListOf<ObjectId>()
    val insertedUuidV4Ids = mutableListOf<ByteArray>()
    val insertedUuidV7Ids = mutableListOf<ByteArray>()
    val insertedSnowflakeIds = mutableListOf<Long>()

    @Setup(Level.Trial)
    fun setup() {
        val uri = System.getProperty(
            "bench.mongo.uri",
            "mongodb://bench:bench123@localhost:27017/pk_bench?authSource=admin"
        )
        mongo = MongoRepository(uri)
        mongo.initialize()
        preload(5_000)
    }

    @TearDown(Level.Trial)
    fun teardown() {
        mongo.close()
    }

    private fun preload(count: Int) {
        repeat(count) { i ->
            val p = "pre-$i"
            insertedObjectIds += mongo.insertObjectId(p)
            UuidV4Generator.generate().also { id ->
                insertedUuidV4Ids += id
                mongo.insertUuidV4(id, p)
            }
            UuidV7Generator.generate().also { id ->
                insertedUuidV7Ids += id
                mongo.insertUuidV7(id, p)
            }
            snowflake.generate().also { id ->
                insertedSnowflakeIds += id
                mongo.insertSnowflake(id, p)
            }
        }
    }

    fun randomObjectId(): ObjectId = insertedObjectIds[ThreadLocalRandom.current().nextInt(insertedObjectIds.size)]
    fun randomUuidV4(): ByteArray = insertedUuidV4Ids[ThreadLocalRandom.current().nextInt(insertedUuidV4Ids.size)]
    fun randomUuidV7(): ByteArray = insertedUuidV7Ids[ThreadLocalRandom.current().nextInt(insertedUuidV7Ids.size)]
    fun randomSnowflake(): Long = insertedSnowflakeIds[ThreadLocalRandom.current().nextInt(insertedSnowflakeIds.size)]
}
