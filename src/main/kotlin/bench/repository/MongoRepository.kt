package bench.repository

import bench.util.bytesToUuid
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.InsertManyOptions
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Instant

/**
 * MongoDB 벤치마크용 레포지토리.
 *
 * WiredTiger 스토리지 엔진:
 * - _id 인덱스 = 클러스터드 인덱스 (MySQL InnoDB와 유사)
 * - ObjectId (시간 정렬) → 순차 삽입 → 스플릿 최소
 * - UUID v4 (랜덤)     → B-tree 중간 삽입 → 스플릿 발생
 *
 * 컬렉션별 _id 전략:
 * - col_objectid  : ObjectId (MongoDB 기본값, 12바이트)
 * - col_uuid_v4   : UUID v4 (랜덤 128비트)
 * - col_uuid_v7   : UUID v7 (시간 정렬 128비트)
 * - col_snowflake : Snowflake (Long)
 */
class MongoRepository(uri: String) : AutoCloseable {

    private val client: MongoClient = MongoClients.create(uri)
    private val db: MongoDatabase = client.getDatabase("pk_bench")

    val colObjectId: MongoCollection<Document> = db.getCollection("col_objectid")
    val colUuidV4: MongoCollection<Document> = db.getCollection("col_uuid_v4")
    val colUuidV7: MongoCollection<Document> = db.getCollection("col_uuid_v7")
    val colSnowflake: MongoCollection<Document> = db.getCollection("col_snowflake")

    val collections = listOf(colObjectId, colUuidV4, colUuidV7, colSnowflake)

    fun initialize() {
        // created_at 인덱스 생성 (Range 쿼리용)
        collections.forEach { col ->
            col.createIndex(Indexes.descending("created_at"), IndexOptions().background(false))
        }
    }

    fun dropAll() {
        collections.forEach { it.drop() }
    }

    // ── INSERT ──────────────────────────────────────────────────────────────

    fun insertObjectId(payload: String): ObjectId {
        val id = ObjectId()
        colObjectId.insertOne(Document()
            .append("_id", id)
            .append("payload", payload)
            .append("created_at", Instant.now())
        )
        return id
    }

    fun insertUuidV4(id: ByteArray, payload: String) {
        colUuidV4.insertOne(Document()
            .append("_id", bytesToUuid(id).toString())
            .append("payload", payload)
            .append("created_at", Instant.now())
        )
    }

    fun insertUuidV7(id: ByteArray, payload: String) {
        colUuidV7.insertOne(Document()
            .append("_id", bytesToUuid(id).toString())
            .append("payload", payload)
            .append("created_at", Instant.now())
        )
    }

    fun insertSnowflake(id: Long, payload: String) {
        colSnowflake.insertOne(Document()
            .append("_id", id)
            .append("payload", payload)
            .append("created_at", Instant.now())
        )
    }

    // ── BULK INSERT ─────────────────────────────────────────────────────────

    fun bulkInsertObjectId(payloads: List<String>) {
        val docs = payloads.map { payload ->
            Document()
                .append("_id", ObjectId())
                .append("payload", payload)
                .append("created_at", Instant.now())
        }
        colObjectId.insertMany(docs, InsertManyOptions().ordered(false))
    }

    fun bulkInsertUuidV4(rows: List<Pair<ByteArray, String>>) {
        val docs = rows.map { (id, payload) ->
            Document()
                .append("_id", bytesToUuid(id).toString())
                .append("payload", payload)
                .append("created_at", Instant.now())
        }
        colUuidV4.insertMany(docs, InsertManyOptions().ordered(false))
    }

    fun bulkInsertUuidV7(rows: List<Pair<ByteArray, String>>) {
        val docs = rows.map { (id, payload) ->
            Document()
                .append("_id", bytesToUuid(id).toString())
                .append("payload", payload)
                .append("created_at", Instant.now())
        }
        colUuidV7.insertMany(docs, InsertManyOptions().ordered(false))
    }

    fun bulkInsertSnowflake(rows: List<Pair<Long, String>>) {
        val docs = rows.map { (id, payload) ->
            Document()
                .append("_id", id)
                .append("payload", payload)
                .append("created_at", Instant.now())
        }
        colSnowflake.insertMany(docs, InsertManyOptions().ordered(false))
    }

    // ── POINT SELECT ────────────────────────────────────────────────────────

    fun selectByObjectId(id: ObjectId): String? =
        colObjectId.find(Filters.eq("_id", id)).first()?.getString("payload")

    fun selectByUuidV4(id: ByteArray): String? =
        colUuidV4.find(Filters.eq("_id", bytesToUuid(id).toString())).first()?.getString("payload")

    fun selectByUuidV7(id: ByteArray): String? =
        colUuidV7.find(Filters.eq("_id", bytesToUuid(id).toString())).first()?.getString("payload")

    fun selectBySnowflake(id: Long): String? =
        colSnowflake.find(Filters.eq("_id", id)).first()?.getString("payload")

    // ── RANGE SELECT ────────────────────────────────────────────────────────

    fun selectRecentByCreatedAt(col: MongoCollection<Document>, limit: Int): List<String> =
        col.find()
            .sort(Document("created_at", -1))
            .limit(limit)
            .map { it.getString("payload") }
            .toList()

    // ── STATS ───────────────────────────────────────────────────────────────

    fun getCollectionStats(colName: String): MongoStats {
        val result = db.runCommand(Document("collStats", colName))
        return MongoStats(
            collectionName = colName,
            count = result.getLong("count") ?: 0L,
            storageSize = result.getLong("storageSize") ?: 0L,
            totalIndexSize = result.getLong("totalIndexSize") ?: 0L,
            avgObjSize = result.getLong("avgObjSize") ?: 0L,
        )
    }

    override fun close() = client.close()
}

data class MongoStats(
    val collectionName: String,
    val count: Long,
    val storageSize: Long,
    val totalIndexSize: Long,
    val avgObjSize: Long,
) {
    val totalSize: Long get() = storageSize + totalIndexSize

    fun summary(): String = buildString {
        append("[mongo/$collectionName] ")
        append("docs=$count ")
        append("storage=${storageSize / 1024}KB ")
        append("index=${totalIndexSize / 1024}KB ")
        append("total=${totalSize / 1024}KB ")
        append("avgObj=${avgObjSize}B")
    }
}
