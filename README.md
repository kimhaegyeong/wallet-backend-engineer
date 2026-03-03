# SentBe Wallet API

> 월렛 동시 출금 및 잔액 무결성 보장 시스템  
> Backend Engineer (Java/Kotlin) 과제 전형

---

## 목차

1. [프로젝트 개요](#프로젝트-개요)
2. [기술 스택](#기술-스택)
3. [실행 방법](#실행-방법)
4. [DB 세팅](#db-세팅)
5. [API 명세](#api-명세)
6. [ID 채번 규칙](#id-채번-규칙)
7. [Timezone 전략](#timezone-전략)
8. [설계 결정](#설계-결정)
9. [테스트 결과](#테스트-결과)

---

## 프로젝트 개요

다수의 요청이 동일한 월렛에서 동시에 출금을 시도할 때, 잔액이 마이너스(Overdraft)가 되거나
트랜잭션이 유실되는 것을 방지하고 데이터 무결성을 보장하는 RESTful API 서버입니다.

**핵심 해결 과제**

- 동시 출금 요청에서의 잔액 무결성 (Race Condition 방지)
- 멱등성(Idempotency) 보장 — 동일 `transactionId` 중복 요청 처리
- 잔액 부족 시 명확한 예외 반환

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.x, Spring Data JPA |
| Database | MySQL 8.0 (Docker) |
| Cache / Lock | Redis 7 (Docker) — 향후 분산 락 확장 대비 |
| Build Tool | Gradle |
| Test | JUnit 5, Spring Boot Test |
| Container | Docker Compose |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI) |

---

## 실행 방법

### 사전 요구사항

- Docker & Docker Compose 설치
- Java 17 이상

### 1. 인프라 실행 (MySQL + Redis)

```bash
docker compose up -d
```

컨테이너 기동 확인:

```bash
docker compose ps
```

정상 기동 시 `mysql`, `redis` 컨테이너가 `healthy` 상태여야 합니다.

### 2. API 서버 실행

```bash
./gradlew bootRun
```

서버 기동 확인:

```
Started WalletApplication in X.XXX seconds
```

기본 포트: `http://localhost:8080`

### 3. 정상 동작 확인

```bash
# 헬스 체크
curl http://localhost:8080/actuator/health

# 출금 요청 (테스트용 walletId — 초기 데이터 참고)
curl -X POST http://localhost:8080/api/v1/wallets/550e8400e29b41d4a716446655440000/withdrawals \
  -H "Content-Type: application/json" \
  -d '{"amount": 10000, "transactionId": "TXN_550e8400e29b41d4a716446655440001"}'

# 거래내역 조회 (날짜 범위 없음 — 전체)
curl http://localhost:8080/api/v1/wallets/550e8400e29b41d4a716446655440000/transactions

# 거래내역 조회 (날짜 범위 지정 — KST 기준, offset 포함)
curl "http://localhost:8080/api/v1/wallets/550e8400e29b41d4a716446655440000/transactions?startDate=2026-01-01T00:00:00%2B09:00&endDate=2026-01-31T23:59:59%2B09:00&page=0&size=20" 
```

### 4. Swagger UI 접속

서버 기동 후 브라우저에서 아래 주소로 접속하면 전체 API를 UI로 확인하고 직접 실행할 수 있습니다.

```
http://localhost:8080/swagger-ui/index.html
```

---

## DB 세팅

### 자동 생성 여부

- **DDL**: 애플리케이션 기동 시 Flyway 마이그레이션으로 자동 생성됩니다.
  (`resources/db/migration/V1__init.sql`)
- **초기 데이터**: 애플리케이션 기동 시 `data.sql`을 통해 자동 삽입됩니다.

### 테이블 구조

**wallet** — 월렛 잔액 관리

```sql
CREATE TABLE wallet (
    wallet_id   VARCHAR(32)   NOT NULL PRIMARY KEY,  -- UUID v4 하이픈 제거 (32자 고정)
    balance     BIGINT        NOT NULL DEFAULT 0,     -- 원 단위 정수 (부동소수점 오차 제거)
    created_at  TIMESTAMP(6)  NOT NULL,               -- UTC 저장
    updated_at  TIMESTAMP(6)  NOT NULL                -- UTC 저장
);
```

**transaction** — 입출금 내역

```sql
CREATE TABLE transaction (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transaction_id    VARCHAR(36)   NOT NULL UNIQUE,  -- TXN_ + UUID v4 하이픈 제거 (멱등성 키)
    wallet_id         VARCHAR(32)   NOT NULL,         -- wallet.wallet_id 참조
    withdrawal_amount BIGINT        NOT NULL,
    balance_after     BIGINT        NOT NULL,
    status            VARCHAR(20)   NOT NULL,         -- SUCCESS | FAILED | DUPLICATE
    response_snapshot JSON,                            -- 멱등 재응답을 위한 최초 응답 캐싱
    withdrawal_date   TIMESTAMP(6)  NOT NULL,               -- UTC 저장, 응답 시 KST 변환
    FOREIGN KEY (wallet_id) REFERENCES wallet(wallet_id),
    INDEX idx_wallet_date (wallet_id, withdrawal_date)
);
```

### 테스트용 초기 데이터

애플리케이션 기동 시 아래 데이터가 자동 삽입됩니다.

```sql
-- wallet_id: UUID v4 하이픈 제거 (VARCHAR 32)
INSERT INTO wallet (wallet_id, balance, created_at, updated_at)
VALUES
    ('550e8400e29b41d4a716446655440000', 1000000, NOW(), NOW()),  -- 동시성 테스트용 (100만원)
    ('6ba7b8109dad11d180b400c04fd430c8',  500000, NOW(), NOW()),  -- 일반 테스트용
    ('6ba7b8119dad11d180b400c04fd430c8',       0, NOW(), NOW());  -- 잔액 부족 테스트용
```

### 수동 삽입 방법 (필요 시)

```bash
docker exec -it sentbe-mysql mysql -u root -proot wallet_db

# wallet_id: UUID v4 하이픈 제거 값을 직접 지정
mysql> INSERT INTO wallet (wallet_id, balance, created_at, updated_at)
       VALUES ('a0eebc99c9b24ef8bb6d457b08a9b37c', 500000, NOW(), NOW());
```

---

## API 명세

### A. 월렛 출금

```
POST /api/v1/wallets/{walletId}/withdrawals
```

**Path Variable**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| walletId | String | 월렛 식별자 |

**Request Body**

```json
{
  "amount": 10000,
  "transactionId": "TXN_550e8400e29b41d4a716446655440001"
}
```

**Response — 성공 (200)**

```json
{
  "success": true,
  "idempotent": false,
  "data": {
    "transactionId": "TXN_550e8400e29b41d4a716446655440001",
    "walletId": "550e8400e29b41d4a716446655440000",
    "withdrawnAmount": 10000,
    "remainingBalance": 90000,
    "withdrawalDate": "2026-03-03T19:00:00+09:00"
  }
}
```

**Response — 멱등 중복 요청 (200)**

```json
{
  "success": true,
  "idempotent": true,
  "data": { /* 최초 처리와 동일한 응답 */ }
}
```

**Response — 잔액 부족 (422)**

```json
{
  "success": false,
  "error": {
    "code": "INSUFFICIENT_BALANCE",
    "message": "출금 가능 잔액이 부족합니다.",
    "currentBalance": 5000,
    "requestedAmount": 10000
  }
}
```

**Response — 월렛 없음 (404)**

```json
{
  "success": false,
  "error": {
    "code": "WALLET_NOT_FOUND",
    "message": "존재하지 않는 월렛입니다."
  }
}
```

---

### B. 거래내역 조회

```
GET /api/v1/wallets/{walletId}/transactions
```

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|------|--------|------|
| startDate | String | N | - | 조회 시작일시 (ISO 8601, KST offset 포함) |
| endDate | String | N | - | 조회 종료일시 (ISO 8601, KST offset 포함) |
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |
| sort | String | N | withdrawalDate,desc | 정렬 기준 |

**날짜 파라미터 규칙**

| 조건 | 처리 |
|------|------|
| startDate, endDate 모두 없음 | 전체 조회 (페이징 적용) |
| startDate만 있음 | 해당 일시 이후 전체 조회 |
| endDate만 있음 | 해당 일시 이전 전체 조회 |
| startDate > endDate | 400 + `INVALID_DATE_RANGE` |
| 조회 범위 > 90일 | 400 + `DATE_RANGE_EXCEEDED` |

날짜는 반드시 offset을 포함한 ISO 8601 형식으로 전달해야 합니다.
서버는 입력값을 UTC로 변환한 뒤 DB 조회에 사용합니다.

```
클라이언트 입력: 2026-01-01T00:00:00+09:00  (KST 자정)
DB 조회 기준:   2025-12-31T15:00:00Z        (UTC 변환)
```

**요청 예시**

```
GET /api/v1/wallets/{walletId}/transactions
    ?startDate=2026-01-01T00:00:00+09:00
    &endDate=2026-01-31T23:59:59+09:00
    &page=0
    &size=20
```

**Response — 성공 (200)**

```json
{
  "success": true,
  "data": {
    "transactions": [
      {
        "transactionId": "TXN_550e8400e29b41d4a716446655440001",
        "walletId": "550e8400e29b41d4a716446655440000",
        "withdrawalAmount": 10000,
        "balance": 90000,
        "withdrawalDate": "2026-03-03T19:00:00+09:00"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "currentPage": 0
  }
}
```

---

### 에러 코드 목록

| 코드 | HTTP Status | 설명 |
|------|-------------|------|
| `WALLET_NOT_FOUND` | 404 | 존재하지 않는 walletId |
| `INSUFFICIENT_BALANCE` | 422 | 잔액 부족 |
| `INVALID_AMOUNT` | 400 | amount ≤ 0 또는 음수 |
| `INVALID_TRANSACTION_ID_FORMAT` | 400 | transactionId 형식 오류 (`TXN_` 미포함 등) |
| `INVALID_DATE_RANGE` | 400 | startDate > endDate |
| `DATE_RANGE_EXCEEDED` | 400 | 조회 범위 90일 초과 |
| `DUPLICATE_TRANSACTION` | 200 | 멱등 중복 요청 (정상 처리) |
| `LOCK_ACQUISITION_TIMEOUT` | 503 | 락 대기 타임아웃 |
| `INTERNAL_SERVER_ERROR` | 500 | 서버 내부 오류 |

---

## Timezone 전략

### 기본 원칙

| 레이어 | Timezone | 이유 |
|--------|----------|------|
| DB 저장 | **UTC** | 환경 독립, 감사 로그 표준, 클라우드 인스턴스 기본값 |
| JVM 동작 | **UTC** | OS timezone 의존 제거 |
| API 응답 | **KST (`+09:00`)** | 한국 사용자 경험, offset 명시로 모호성 제거 |
| Timezone 설정 | **`app.timezone.display`** | 설정값 한 줄 변경으로 리전 확장 대응 |

DB에 KST를 저장하지 않는 이유는, AWS/GCP 인스턴스의 기본 timezone이 UTC이므로
서버 환경과 저장값이 어긋나는 버그를 원천 차단하고, 향후 정산/감사 로그에서
UTC 기준점이 없으면 시간 역추적이 불가능해지기 때문입니다.

### 데이터 흐름

```
출금 요청 수신
    │
    ▼
Instant.now()                              → UTC 절대 시각 생성
    │
    ▼
TIMESTAMP(6) 컬럼 INSERT                   → UTC로 DB 저장
    │
    ▼
Jackson 직렬화
    │  app.timezone.display = Asia/Seoul
    ▼
API 응답: "2026-03-03T19:00:00+09:00"      → KST + offset 명시
```

### 응답 포맷

```json
"withdrawalDate": "2026-03-03T19:00:00+09:00"
```

단순 `"2026-03-03 19:00:00"` 형식을 사용하지 않는 이유는 offset(`+09:00`)이 없으면
수신자가 이 값이 KST인지 UTC인지 추론해야 하기 때문입니다.
ISO 8601 형식에 offset을 포함하면 설계 의도가 코드에서 직접 드러납니다.

### 확장 시나리오

현재 `app.timezone.display=Asia/Seoul` 설정 한 줄만 변경하면
멀티 리전 또는 글로벌 서비스 전환 시 응답 timezone을 즉시 교체할 수 있습니다.

---

## 설계 결정

### 동시성 제어 기법: DB 비관적 락 (SELECT FOR UPDATE)

#### 선택 이유

출금은 **충돌 빈도가 높은 연산**입니다. 동일 월렛에 요청이 집중될 때, 낙관적 락(`@Version`)은
`OptimisticLockException` → 재시도를 반복하며 오히려 DB 부하를 증폭시킵니다.

비관적 락은 "먼저 락을 잡고, 처리한 뒤 해제"하는 방식으로 충돌 자체를 직렬화합니다.
금융 도메인에서 **잔액 정확성 > 처리량**이라는 우선순위를 명확히 반영한 선택입니다.

```
요청 → SELECT wallet FOR UPDATE  (락 획득)
         → 잔액 검증
           → UPDATE balance
             → INSERT transaction
               → COMMIT  (락 해제)
```

#### 성능 트레이드오프

| 항목 | 내용 |
|------|------|
| 장점 | 재시도 없이 정확한 1회 처리 보장, 구현 단순 |
| 장점 | 잔액 Overdraft 원천 차단 |
| 단점 | 동일 월렛 요청이 직렬화되어 TPS 제한 |
| 단점 | 락 대기 중 DB 커넥션 점유 → 커넥션 풀 고갈 위험 |

#### 멱등성 구현: 2중 방어

1. **1차 — 서비스 레이어 선조회**: `transactionId` 존재 시 `response_snapshot` 캐싱된 응답 즉시 반환
2. **2차 — DB UNIQUE 제약**: 동시 중복 요청이 1차를 뚫어도 INSERT 시 `DuplicateKeyException` 발생 → 멱등 처리


### 날짜 범위 조회 전략

#### 최대 조회 범위 90일 제한

날짜 범위에 상한선을 두지 않으면 수백만 건이 한 번에 조회될 수 있습니다.
`app.transaction.max-date-range-days=90` 설정값으로 관리하여 하드코딩 없이 운영 중 조정이 가능합니다.

#### KST 입력 → UTC 변환 후 조회

클라이언트는 KST 기준으로 날짜를 입력하지만, DB는 UTC로 저장되어 있습니다.
서비스 레이어에서 반드시 UTC로 변환한 뒤 쿼리해야 합니다.

```
OffsetDateTime.parse("2026-01-01T00:00:00+09:00")
    .toInstant()  →  2025-12-31T15:00:00Z  (이 값으로 WHERE 절 조회)
```

변환 없이 KST 값 그대로 쿼리하면 9시간 오차가 발생합니다.

#### 인덱스 활용

```sql
INDEX idx_wallet_date (wallet_id, withdrawal_date)
```

`wallet_id`로 먼저 필터링하고 `withdrawal_date` 범위를 인덱스 범위 스캔으로 처리합니다.
날짜 범위 조회 쿼리에 별도의 인덱스 추가 없이 기존 복합 인덱스를 그대로 활용합니다.

#### COUNT 쿼리 트레이드오프

Spring Data JPA `Page<T>`는 전체 건수를 위해 COUNT 쿼리를 별도 실행합니다.
거래량이 많은 월렛에서 90일치 COUNT는 비용이 클 수 있으나,
현재 과제 스코프에서는 `Page<T>`를 유지하고 향후 트래픽에 따라 `Slice<T>` 전환을 검토합니다.

#### 우려사항 및 향후 대책

| 우려사항 | 향후 대책 |
|----------|-----------|
| 동일 월렛 고빈도 집중 시 처리량 저하 | 월렛 단위 요청 큐(Queue) 도입, 이벤트 소싱 전환 |
| DB 커넥션 고갈 | HikariCP 커넥션 풀 튜닝, `innodb_lock_wait_timeout` 설정 |
| 멀티 인스턴스 확장 시 락 경합 증가 | Redisson 기반 Redis 분산 락으로 전환 |
| 단일 DB 장애 | Read Replica 분리, 장애 시 Circuit Breaker 적용 |

---

## ID 채번 규칙

| 항목 | wallet_id | transaction_id |
|------|-----------|----------------|
| 생성 주체 | 서버 (월렛 생성 시 자동 발급) | 클라이언트 (요청 시 직접 생성) |
| 형식 | UUID v4 하이픈 제거 | `TXN_` + UUID v4 하이픈 제거 |
| 예시 | `550e8400e29b41d4a716446655440000` | `TXN_550e8400e29b41d4a716446655440001` |
| DB 타입 | `VARCHAR(32)` | `VARCHAR(36)` |
| 제약 | PK | UNIQUE INDEX |
| 목적 | 리소스 식별 | 멱등성 보장 |

`transaction_id`는 클라이언트가 생성하므로 서버는 형식 검증만 수행합니다.
프리픽스(`TXN_`)는 로그 및 디버깅 시 ID 유형을 즉시 식별하기 위해 사용하며,
향후 입금(`DEP_`), 환불(`RFD_`) 등 유형 확장에도 일관된 규칙을 적용합니다.

---

## 테스트 결과

### 동시성 테스트 시나리오

| 항목 | 내용 |
|------|------|
| 초기 잔액 | 1,000,000원 |
| 동시 스레드 수 | 100개 |
| 건당 출금액 | 10,000원 |
| 예상 성공 건수 | 100건 |
| 예상 최종 잔액 | 0원 |

### 검증 조건

```
1. finalBalance >= 0                          (Overdraft 방지)
2. successCount * 10,000 + finalBalance == 1,000,000  (총액 무결성)
3. successCount + failCount == 100             (트랜잭션 유실 없음)
4. DB transaction 레코드 수 == successCount    (중복 저장 없음)
```

### 멱등성 테스트 시나리오

동일한 `transactionId`로 50회 동시 요청 → DB에 레코드 **1건만** 생성, 모든 응답 동일 확인

### 테스트 실행 방법

```bash
./gradlew test --tests "com.sentbe.wallet.concurrency.*"
```

> 상세 테스트 결과 (로그 및 캡처)는 [테스트 결과 문서](./TEST_RESULTS.md)를 참조하세요.
