# PK Bench

MySQL InnoDB / PostgreSQL / MongoDB WiredTiger 환경에서 PK 전략별 성능을 비교하는 벤치마크 프로젝트.

---

## 목차

1. [핵심 개념](#1-핵심-개념)
2. [PK 전략별 설명](#2-pk-전략별-설명)
3. [DB 엔진별 저장 구조](#3-db-엔진별-저장-구조)
4. [PK 전략 × 엔진 조합 분석](#4-pk-전략--엔진-조합-분석)
5. [벤치마크 구성](#5-벤치마크-구성)
6. [실행 방법](#6-실행-방법)

---

## 1. 핵심 개념

### 클러스터드 인덱스 (Clustered Index)

데이터 행 자체가 인덱스 순서대로 물리 저장되는 구조. **인덱스 = 데이터 파일**.

```
일반 인덱스 (Non-Clustered):
  인덱스 파일: id=1 → 페이지47, id=2 → 페이지3, ...
  데이터 파일: 별도 존재 (힙)
  조회: 인덱스 탐색 → 데이터 파일 접근 (2단계)

클러스터드 인덱스:
  데이터 파일 = B-tree 그 자체
  리프 페이지: [id=1, name="홍길동", ...] [id=2, ...] ...
  조회: B-tree 탐색 → 바로 데이터 (1단계)
```

### 페이지 (Page)

InnoDB/WiredTiger의 I/O 기본 단위 = **16KB 연속 블록**.

```
페이지 내부 (물리적으로 연속된 16KB):
  [페이지 헤더][행1][행2]...[행N][빈 공간][페이지 디렉토리]

페이지 간: 물리적 연속 보장 안 됨
  B-tree 링크(prev/next 포인터)로 논리 순서만 연결
```

### 페이지 스플릿 (Page Split)

페이지가 꽉 찬 상태에서 중간에 새 행을 삽입할 때 발생.

```
삽입 전 (꽉 참):
  페이지A: [10][20][30][40][50][60][70][80]

"35" 삽입 시도 → 30과 40 사이인데 자리 없음 → 스플릿:
  페이지A: [10][20][30][35][40]   ← 절반 + 새 값
  페이지B: [50][60][70][80]       ← 나머지 절반 (새 위치에 할당)

비용: 페이지 읽기 + 페이지 쓰기 2회 + Redo Log 쓰기
```

### 페이지 밀도 (Page Density)

하나의 페이지(16KB)에 실제로 채워진 비율.

```
AUTO_INCREMENT (스플릿 없음):
  페이지: [행1~100] → 95% 채움
  1000건 조회 = 10페이지 읽기

UUID v4 (스플릿 빈번):
  페이지A: [행1~50][빈공간] → 50% 채움
  페이지B: [행51~100][빈공간] → 50% 채움
  1000건 조회 = 20페이지 읽기  ← 2배 더 읽어야 함
```

> 페이지 밀도는 물리적 위치와 무관. 페이지가 어디 있든 **그 안이 얼마나 찼냐**의 문제.

### Secondary Index

PK 외 컬럼에 생성하는 인덱스. 리프 노드에 **인덱스 컬럼 값 + PK 값** 저장.

```
테이블: (id BIGINT PK, name VARCHAR, age INT, INDEX idx_age(age))

Secondary Index (idx_age) 리프:
  [age=25, id=2]
  [age=30, id=1]
  [age=30, id=3]

SELECT name WHERE age=30:
  1단계: idx_age 탐색 → id=1, id=3 발견
  2단계: 클러스터드 인덱스에서 id=1, id=3 각각 조회 → 이중 접근
```

**PK 크기가 모든 Secondary Index 크기에 영향:**

```
BIGINT PK(8B) Secondary Index 항목: [age(4B) + id(8B)]  = 12B
UUID PK(16B)  Secondary Index 항목: [age(4B) + id(16B)] = 20B → 67% 더 큼
```

### 단편화 / Bloat

실제 데이터보다 저장 공간이 과도하게 커진 상태.

```
MySQL: DATA_FREE (페이지 내 빈 공간 누적)
  원인: 스플릿 후 각 페이지가 50% 수준으로만 채워짐
  측정: information_schema.TABLES.DATA_FREE
  해결: OPTIMIZE TABLE (테이블 재구성, 잠금 주의)

PostgreSQL: Dead Tuple (MVCC로 인한 이전 버전 행 잔존)
  원인: UPDATE/DELETE 시 기존 행을 즉시 삭제하지 않고 dead tuple로 표시
  측정: pg_stat_user_tables.n_dead_tup
  해결: VACUUM (백그라운드 정리)

MongoDB: Collection/Index Bloat
  원인: 문서 삭제/교체 후 WiredTiger가 공간을 즉시 반환하지 않음
  측정: collStats.storageSize vs dataSize 차이
  해결: compact 명령
```

### JIT (Just-In-Time Compiler)

JVM이 자주 실행되는 코드를 실시간으로 기계어로 변환하는 최적화.

```
1회차 실행: 인터프리터 → 10ms (느림)
100회차:    JVM이 "핫코드" 감지 → JIT 컴파일 시작
200회차:    기계어 직접 실행 → 3ms (빠름)

→ JMH 워밍업: JIT 안정화 대기 후 측정값 수집
  워밍업 3회(버림) → 측정 5회(사용)
```

---

## 2. PK 전략별 설명

### BIGINT AUTO_INCREMENT

```sql
id BIGINT NOT NULL AUTO_INCREMENT  -- 1, 2, 3, 4 ...
```

DB가 삽입 시마다 +1 발급.

| 항목 | 내용 |
|---|---|
| 크기 | 8바이트 |
| 정렬 | 항상 단조증가 |
| 분산 생성 | 불가 (단일 시퀀스) |
| 보안 | 레코드 수 추측 가능 |

---

### UUID v4

```
550e8400-e29b-41d4-a716-446655440000
└─────────────── 완전 랜덤 128비트 ───────────────┘
```

| 항목 | 내용 |
|---|---|
| 크기 | 16바이트 (BINARY(16) 저장 시) |
| 정렬 | 완전 랜덤 |
| 분산 생성 | 가능 |
| 보안 | 예측 불가 |

---

### UUID v7

```
0190f6e4-5d2b-7000-a000-123456789abc
└──── 48비트 타임스탬프 ────┘└── 랜덤 ──┘
```

RFC 9562 표준. 앞 48비트 = Unix epoch ms → **시간순 정렬**.

| 항목 | 내용 |
|---|---|
| 크기 | 16바이트 |
| 정렬 | 밀리초 단위 시간순 |
| 분산 생성 | 가능 |
| 보안 | 타임스탬프 노출, 나머지는 랜덤 |

---

### ULID

```
01ARZ3NDEKTSV4RRFFQ69G5FAV
└── 48비트 타임스탬프 ──┘└── 80비트 랜덤 ──┘
           Base32 인코딩 26자
```

UUID v7과 구조적으로 동일. 이 프로젝트는 **Monotonic ULID** 사용 (같은 ms 내 순서 보장).

---

### Snowflake ID

```
927561798458011648
└ 41비트 타임스탬프 ┘└ 5비트 DC ┘└ 5비트 워커 ┘└ 12비트 시퀀스 ┘
```

Twitter 설계. BIGINT에 저장. 초당 노드당 최대 4096개 생성.

| 항목 | 내용 |
|---|---|
| 크기 | 8바이트 |
| 정렬 | 밀리초 단위 시간순 |
| 분산 생성 | 가능 (워커 ID로 구분) |
| 보안 | 타임스탬프/노드 정보 노출 |

---

### Composite Key `(created_date, seq_id)`

```sql
PRIMARY KEY (created_date, seq_id)
-- ('2026-05-20', 1), ('2026-05-20', 2), ('2026-05-21', 1) ...
```

날짜별 데이터 분리. 날짜 범위 쿼리에서 클러스터드 인덱스 직접 활용.

| 항목 | 내용 |
|---|---|
| 크기 | 4(DATE) + 8(BIGINT) = 12바이트 |
| 정렬 | 날짜 → 시퀀스 순 |
| 단건 조회 | 두 컬럼 모두 필요 |
| 적합한 경우 | 날짜 기반 파티셔닝, 시계열 데이터 |

---

## 3. DB 엔진별 저장 구조

### MySQL InnoDB — 클러스터드 인덱스

```
.ibd 파일 = B-tree 자체 (데이터와 인덱스가 하나)

리프 페이지:
  [id=1, name="홍길동", age=30]  ← 행 전체가 PK 순서로 저장
  [id=2, name="김철수", age=25]
  [id=3, name="이영희", age=30]

PK 순서 = 물리 저장 순서 (논리적으로)
→ UUID v4 랜덤 삽입 → 페이지 스플릿 → 쓰기 비용 급증
→ 순차 PK → 항상 마지막 페이지에 append → 스플릿 없음
```

**Range SELECT 이점:**
```sql
SELECT * FROM t ORDER BY id DESC LIMIT 100;
-- PK = 클러스터드 인덱스 → B-tree 역순 스캔 → 1단계
-- 가장 빠른 Range 조회
```

---

### PostgreSQL — 힙 스토리지

```
힙 파일: 삽입 순서대로 행을 기록 (PK 순서와 무관)

힙 페이지:
  [id=5, "홍길동"]  ← 먼저 삽입됨
  [id=2, "김철수"]
  [id=8, "이영희"]

PK 인덱스 (별도 B-tree):
  id=2 → 힙 페이지1, 2번째
  id=5 → 힙 페이지1, 1번째
  id=8 → 힙 페이지1, 3번째
```

**UUID v4 INSERT 영향이 MySQL보다 작은 이유:**
```
MySQL: UUID v4 삽입 → 클러스터드 인덱스 중간에 끼워넣기 → 스플릿
PostgreSQL: UUID v4 삽입 → 힙 끝에 append → 힙 삽입 자체는 괜찮음
            단, PK B-tree 인덱스에서는 랜덤 스플릿 발생 (인덱스 bloat)
```

**Range SELECT 차이:**
```sql
SELECT * FROM t ORDER BY id DESC LIMIT 100;
-- PostgreSQL: PK B-tree 탐색 → 힙에서 행 가져오기 (2단계)
-- MySQL보다 느림 (클러스터드 스캔 이점 없음)
```

---

### MongoDB WiredTiger — 클러스터드 B-tree + 도큐먼트

```
컬렉션 파일 = B-tree (_id 기반 클러스터드, MySQL InnoDB와 유사)

리프 페이지:
  [_id=ObjectId("..."), {payload: "...", created_at: ...}]
  [_id=ObjectId("..."), {payload: "...", created_at: ...}]

ObjectId (시간 정렬) → 순차 삽입 → 스플릿 최소 (AUTO_INCREMENT와 유사)
UUID v4 → 랜덤 삽입 → B-tree 스플릿 발생 (MySQL InnoDB와 유사)
```

**MySQL과의 차이:**
```
MySQL:   스키마 강제, ACID 트랜잭션, row-level lock
MongoDB: 유연한 스키마, document-level lock
         insertMany(ordered=false) → 병렬 삽입 → 배치 INSERT에서 유리
         MVCC 없음 → Dead Tuple 없음 (대신 journaling)
```

---

## 4. PK 전략 × 엔진 조합 분석

### INSERT 성능

```
MySQL InnoDB:
  AUTO_INCREMENT ≈ Snowflake > UUID v7 ≈ ULID >> UUID v4
  (클러스터드 인덱스 스플릿 여부가 결정적)

PostgreSQL:
  모든 전략이 비슷 (힙 append는 동일)
  단, UUID v4 → PK B-tree 인덱스 bloat 누적 → 장기적으로 저하

MongoDB WiredTiger:
  ObjectId ≈ Snowflake > UUID v7 ≈ ULID >> UUID v4
  (MySQL과 유사, 단 배치에서 ordered=false 병렬 처리로 전반적으로 빠를 수 있음)
```

### Point SELECT 성능

```
세 DB 모두: B-tree O(log n) → 전략 간 차이 작음
차이 발생 요인: Buffer Pool / Shared Buffers / WiredTiger Cache 히트율

순차 PK (AUTO_INCREMENT, Snowflake, UUID v7):
  최근 삽입 데이터가 캐시에 몰려있음 → 히트율 높음

UUID v4:
  전체 인덱스에 랜덤 분포 → 캐시 미스 증가 → 디스크 I/O 발생
```

### Range SELECT 성능

```
MySQL InnoDB:
  ORDER BY id DESC (PK 기반):
    AUTO_INCREMENT, Snowflake → 클러스터드 인덱스 직접 역순 스캔 → 최고 성능
  ORDER BY created_at DESC (Secondary Index 기반):
    → Secondary Index 탐색 → PK로 클러스터드 인덱스 이중 접근
    UUID v4 → Secondary Index도 단편화 → 추가 비용

PostgreSQL:
  ORDER BY id DESC or created_at DESC 모두 인덱스 → 힙 2단계
  전략 간 차이가 MySQL보다 작음

MongoDB:
  ORDER BY created_at DESC → created_at 인덱스 → 도큐먼트 조회 (2단계)
  PostgreSQL과 유사한 패턴
```

### 스토리지 크기

```
PK 크기 비교:
  BIGINT (AUTO_INCREMENT, Snowflake): 8바이트
  UUID (v4/v7/ULID):                 16바이트
  Composite (DATE + BIGINT):         12바이트
  MongoDB ObjectId:                  12바이트

Secondary Index 크기:
  UUID PK → 모든 Secondary Index에 16바이트 포함
  → Secondary Index가 많을수록 UUID의 스토리지 불리함이 커짐

단편화 (UUID v4):
  MySQL: DATA_FREE 증가 (스플릿 후 빈 공간)
  PostgreSQL: 인덱스 bloat (B-tree 페이지 밀도 저하)
  MongoDB: Collection bloat
```

### SSD 환경에서의 차이

```
SSD가 완화하는 것:
  Random I/O 페널티 (물리적으로 흩어진 페이지 접근 비용)

SSD가 완화하지 못하는 것:
  페이지 스플릿 쓰기 비용 (I/O 횟수 자체가 더 많음)
  페이지 밀도 저하 (같은 데이터를 더 많은 페이지에서 읽어야 함)
  Buffer Pool 효율 저하 (캐시에 올릴 수 있는 유효 행 수 감소)
```

### 서비스 규모별 전략 권장

| 규모 | 권장 전략 | 이유 |
|---|---|---|
| 소규모 단일 서버 | AUTO_INCREMENT | 단순, 최고 성능 |
| 중규모 단일 서버 | Snowflake 또는 UUID v7 | 분산 대비, 성능 손실 최소 |
| 대규모 분산 | Snowflake | BIGINT 크기로 분산 생성 |
| 보안 중요 | UUID v7 | PK 예측 불가, 시간 정렬 유지 |
| 날짜 기반 파티셔닝 | Composite Key | 날짜 범위 쿼리 최적화 |
| 레거시/호환성 | UUID v4 | 가장 널리 지원, 성능 절충 |

---

## 5. 벤치마크 구성

### 기술 스택

- **언어**: Kotlin 2.1 + JDK 21
- **벤치마크**: JMH 1.37 (Java Microbenchmark Harness)
- **DB 접근**: 순수 JDBC + MongoDB Driver Sync (ORM 없음)
- **DB**: MySQL 8.0 / PostgreSQL 16 / MongoDB 7.0 (Docker)

### 벤치마크 시나리오

| 클래스 | 측정 내용 | 모드 |
|---|---|---|
| `SingleInsertBenchmark` | 단건 INSERT (MySQL/PG) | Throughput, AvgTime |
| `BulkInsertBenchmark` | 1000건 배치 INSERT | AvgTime |
| `PointSelectBenchmark` | PK 단건 조회 @Threads(4) | Throughput, AvgTime |
| `RangeSelectBenchmark` | ORDER BY created_at/PK DESC LIMIT N | AvgTime |
| `MongoSingleInsertBenchmark` | MongoDB 단건 INSERT | Throughput, AvgTime |
| `MongoBulkInsertBenchmark` | MongoDB 배치 INSERT | AvgTime |
| `MongoPointSelectBenchmark` | MongoDB _id 조회 | Throughput, AvgTime |
| `MongoRangeSelectBenchmark` | MongoDB created_at 범위 조회 | AvgTime |

### 분석 쿼리

| 파일 | 내용 |
|---|---|
| `scripts/analyze_mysql.sql` | 단편화율, 버퍼풀 히트율, Secondary Index 크기 비율 |
| `scripts/analyze_postgresql.sql` | 힙 bloat, B-tree 페이지 밀도, 캐시 히트율 |
| `scripts/analyze_mongo.js` | WiredTiger 캐시 히트율, 컬렉션 스토리지 분석 |

### 프로젝트 구조

```
pk-bench/
├── docker-compose.yml                    MySQL + PostgreSQL + MongoDB
├── build.gradle.kts                      JMH 0.7.2 플러그인
├── src/
│   ├── main/kotlin/bench/
│   │   ├── config/
│   │   │   ├── DbDialect.kt             mysql / postgresql 분기
│   │   │   └── DataSourceConfig.kt      HikariCP 설정
│   │   ├── generator/
│   │   │   ├── UuidV4Generator.kt
│   │   │   ├── UuidV7Generator.kt
│   │   │   ├── UlidGenerator.kt
│   │   │   └── SnowflakeGenerator.kt
│   │   ├── repository/
│   │   │   ├── BenchRepository.kt       MySQL/PG JDBC
│   │   │   └── MongoRepository.kt       MongoDB Driver
│   │   └── schema/
│   │       ├── TableDefinitions.kt      MySQL/PG DDL (방언별 분리)
│   │       └── SchemaInitializer.kt
│   └── jmh/kotlin/bench/
│       ├── BenchmarkState.kt            MySQL/PG JMH @State
│       ├── SingleInsertBenchmark.kt
│       ├── BulkInsertBenchmark.kt
│       ├── PointSelectBenchmark.kt
│       ├── RangeSelectBenchmark.kt
│       ├── MongoBenchmarkState.kt       MongoDB JMH @State
│       ├── MongoInsertBenchmark.kt
│       └── MongoSelectBenchmark.kt
└── scripts/
    ├── setup.sh
    ├── run-bench.sh
    ├── analyze_mysql.sql
    ├── analyze_postgresql.sql
    └── analyze_mongo.js
```

---

## 6. 실행 방법

### 사전 요구사항

- Docker Desktop
- JDK 21+
- Gradle 8.9+

### 실행

```bash
# 1. 컨테이너 기동 + 빌드
./scripts/setup.sh

# 2. 전체 벤치마크 실행 (MySQL + PostgreSQL + MongoDB)
./scripts/run-bench.sh

# 3. 특정 벤치마크만 실행
./scripts/run-bench.sh SingleInsert      # 단건 INSERT만
./scripts/run-bench.sh Mongo             # MongoDB 관련만
./scripts/run-bench.sh '.*' mysql        # MySQL만

# 4. 결과 확인
cat results/<timestamp>/results.json
cat results/<timestamp>/mysql_storage.txt
cat results/<timestamp>/pg_storage.txt
cat results/<timestamp>/mongo_storage.txt
```

### DB 접속 정보

| DB | Host | Port | Database | User | Password |
|---|---|---|---|---|---|
| MySQL | localhost | 3306 | pk_bench | bench | bench123 |
| PostgreSQL | localhost | 5432 | pk_bench | bench | bench123 |
| MongoDB | localhost | 27017 | pk_bench | bench | bench123 |
