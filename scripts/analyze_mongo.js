// ============================================================
// MongoDB WiredTiger: PK 전략별 스토리지 및 인덱스 분석
// 실행: mongosh "mongodb://bench:bench123@localhost:27017/pk_bench?authSource=admin" scripts/analyze_mongo.js
// ============================================================

const collections = ["col_objectid", "col_uuid_v4", "col_uuid_v7", "col_snowflake"];

print("\n=== Collection Storage Comparison ===\n");
print(["Collection", "Docs", "StorageMB", "IndexMB", "TotalMB", "AvgDocBytes"].join("\t"));

collections.forEach(colName => {
    try {
        const stats = db.runCommand({ collStats: colName });
        const storageMB = (stats.storageSize / 1024 / 1024).toFixed(2);
        const indexMB   = (stats.totalIndexSize / 1024 / 1024).toFixed(2);
        const totalMB   = ((stats.storageSize + stats.totalIndexSize) / 1024 / 1024).toFixed(2);
        const avgObj    = stats.avgObjSize || 0;
        print([colName, stats.count, storageMB, indexMB, totalMB, avgObj].join("\t"));
    } catch(e) {
        print(`${colName}: 없음 또는 오류`);
    }
});

print("\n=== Index Details ===\n");
collections.forEach(colName => {
    try {
        const idxStats = db[colName].aggregate([{ $indexStats: {} }]).toArray();
        idxStats.forEach(idx => {
            print(`[${colName}] index=${idx.name} accesses=${idx.accesses.ops}`);
        });
    } catch(e) {}
});

print("\n=== WiredTiger Cache Stats ===\n");
const serverStatus = db.runCommand({ serverStatus: 1 });
const wt = serverStatus.wiredTiger;
if (wt) {
    const cacheStats = wt.cache;
    const hits   = cacheStats["pages requested from the cache"];
    const misses = cacheStats["pages read into cache"];
    const hitRate = hits ? ((hits - misses) / hits * 100).toFixed(2) : "N/A";
    print(`Cache hit rate: ${hitRate}%`);
    print(`Pages in cache: ${cacheStats["pages currently held in the cache"]}`);
    print(`Cache used MB: ${(cacheStats["bytes currently in the cache"] / 1024 / 1024).toFixed(2)}`);
}

print("\n=== _id Type Size Comparison ===\n");
print("ObjectId  = 12 bytes (time-ordered, sequential inserts)");
print("UUID v4   = 36 bytes as string / 16 bytes as Binary (random inserts)");
print("UUID v7   = 36 bytes as string / 16 bytes as Binary (time-ordered)");
print("Snowflake = 8 bytes (Long, time-ordered)");
