# TEST_CASES.md — 테스트 케이스 명세

> SentBe Wallet API 통합 테스트 전체 케이스 정의서
> 실행 후 각 케이스의 **실제 결과**란을 기재하여 제출합니다.

---

## 범례

| 기호 | 의미 |
|------|------|
| ★★★ | 필수 — 반드시 통과해야 함 |
| ★★☆ | 권장 — 통과 권장, 미구현 시 사유 기재 |
| ★☆☆ | 선택 — 여유 시 구현 |
| ✅ | Pass |
| ❌ | Fail |
| ⬜ | 미실행 |

---

## A. 동시성 제어 테스트

> 클래스: `WalletConcurrencyTest`
> 공통 설정: `@SpringBootTest`, `@Transactional` 미사용

---

### A-1. 정상 소진 시나리오 ★★★

**목적** 100개 스레드가 동시에 출금할 때 잔액이 정확히 소진되고 총액 무결성이 보장되는가

**사전 조건**

```sql
INSERT INTO wallet (wallet_id, balance, created_at, updated_at)
VALUES ('test-wallet-a1', 1000000, NOW(), NOW());
```

**입력**

| 항목 | 값 |
|------|----|
| walletId | `test-wallet-a1` |
| 스레드 수 | 100 |
| 건당 출금액 | 10,000원 |
| 총 요청액 | 1,000,000원 |
| transactionId | 스레드별 고유값 (`TXN_` + UUID) |

**실행 방법**

```java
CountDownLatch startLatch = new CountDownLatch(1);
CountDownLatch doneLatch  = new CountDownLatch(100);
ExecutorService executor  = Executors.newFixedThreadPool(100);

// 100개 스레드 준비 → startLatch.await() 대기
// startLatch.countDown() → 일제 출발
// doneLatch.await() → 전체 완료 대기
```

**기대 결과**

| 검증 항목 | 기대값 |
|-----------|--------|
| finalBalance | 0원 |
| successCount | 100 |
| failCount | 0 |
| DB transaction 레코드 수 | 100건 |
| successCount × 10,000 + finalBalance | 1,000,000원 |

**실제 결과**

| 검증 항목 | 실제값 | Pass/Fail |
|-----------|--------|-----------|
| finalBalance | | ⬜ |
| successCount | | ⬜ |
| failCount | | ⬜ |
| DB transaction 레코드 수 | | ⬜ |
| 총액 무결성 | | ⬜ |

---

### A-2. Overdraft 방지 시나리오 ★★★

**목적** 잔액을 초과하는 동시 출금 요청에서 잔액이 절대 음수가 되지 않는가

**사전 조건**

```sql
INSERT INTO wallet (wallet_id, balance, created_at, updated_at)
VALUES ('test-wallet-a2', 50000, NOW(), NOW());
```

**입력**

| 항목 | 값 |
|------|----|
| walletId | `test-wallet-a2` |
| 스레드 수 | 100 |
| 건당 출금액 | 10,000원 |
| 총 요청액 | 1,000,000원 (초기 잔액 50,000원 초과) |

**기대 결과**

| 검증 항목 | 기대값 |
|-----------|--------|
| finalBalance | 0원 (음수 절대 불가) |
| successCount | 5 |
| failCount | 95 (INSUFFICIENT_BALANCE) |
| DB transaction 레코드 수 | 100건 (성공 5 + 실패 95) |
| successCount × 10,000 + finalBalance | 50,000원 |

**실제 결과**

| 검증 항목 | 실제값 | Pass/Fail |
|-----------|--------|-----------|
| finalBalance | | ⬜ |
| successCount | | ⬜ |
| failCount | | ⬜ |
| DB transaction 레코드 수 | | ⬜ |
| 총액 무결성 | | ⬜ |

---

### A-3. 락 제거 대조군 ★★☆

**목적** `SELECT FOR UPDATE` 제거 시 동시성 버그가 실제로 발생함을 증명하여 락의 필요성을 입증

**방법** `WalletRepository.findByIdWithLock()`에서 `@Lock` 어노테이션 제거 후 A-2와 동일 조건 실행

**기대 결과 (버그 발생 확인)**

아래 중 하나 이상이 발생해야 합니다.

| 검증 항목 | 기대 (버그) |
|-----------|-------------|
| finalBalance | 0 미만 (음수) |
| successCount | 5 초과 |
| DB 레코드 수 | 실제 처리 수와 불일치 |

**실제 결과**

| 검증 항목 | 실제값 | 버그 발생 여부 |
|-----------|--------|---------------|
| finalBalance | | ⬜ |
| successCount | | ⬜ |
| 비고 | | ⬜ |

> 실행 로그 또는 스크린샷을 `TEST_RESULTS.md`에 첨부하세요.

---

### A-4. 단일 순차 베이스라인 ★☆☆

**목적** 동시성 없는 정상 흐름 검증 — 이 케이스가 깨지면 비즈니스 로직 자체가 잘못된 것

**사전 조건**

```sql
INSERT INTO wallet (wallet_id, balance, created_at, updated_at)
VALUES ('test-wallet-a4', 100000, NOW(), NOW());
```

**입력** 1 스레드 / 10회 순차 출금 / 건당 10,000원

**기대 결과**

| 회차 | 출금 후 기대 잔액 |
|------|-----------------|
| 1회차 | 90,000원 |
| 2회차 | 80,000원 |
| ... | ... |
| 10회차 | 0원 |

**실제 결과**

| 검증 항목 | 실제값 | Pass/Fail |
|-----------|--------|-----------|
| 10회차 finalBalance | | ⬜ |
| DB 레코드 순서와 balance_after 일치 | | ⬜ |

---

## B. 멱등성 테스트

> 클래스: `WalletIdempotencyTest`

---

### B-1. 순차 중복 요청 차단 ★★★

**목적** 동일한 transactionId로 여러 번 요청이 와도 잔액이 1회만 차감되는가

**사전 조건**

```sql
INSERT INTO wallet (wallet_id, balance, created_at, updated_at)
VALUES ('test-wallet-b1', 100000, NOW(), NOW());
```

**입력**

| 항목 | 값 |
|------|----|
| walletId | `test-wallet-b1` |
| amount | 10,000원 |
| transactionId | `TXN_b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1` (고정) |
| 요청 횟수 | 5회 (순차) |

**기대 결과**

| 요청 | 기대 응답 | 기대 잔액 |
|------|----------|----------|
| 1회차 | success=true, idempotent=false | 90,000원 |
| 2회차 | success=true, idempotent=true | 90,000원 (변경 없음) |
| 3회차 | success=true, idempotent=true | 90,000원 (변경 없음) |
| 4회차 | success=true, idempotent=true | 90,000원 (변경 없음) |
| 5회차 | success=true, idempotent=true | 90,000원 (변경 없음) |

| 검증 항목 | 기대값 |
|-----------|--------|
| DB transaction 레코드 수 | 1건 |
| 최종 잔액 | 90,000원 |
| 모든 응답의 remainingBalance | 90,000원 (동일) |

**실제 결과**

| 검증 항목 | 실제값 | Pass/Fail |
|-----------|--------|-----------|
| DB transaction 레코드 수 | | ⬜ |
| 최종 잔액 | | ⬜ |
| 2~5회차 idempotent=true | | ⬜ |

---

### B-2. 동시 중복 요청 레이스 컨디션 ★★★

**목적** 동일 transactionId로 동시에 50개 요청이 오더라도 DB에 1건만 저장되는가
(1차 방어: 서비스 선조회 / 2차 방어: DB UNIQUE → DuplicateKeyException catch 동시 검증)

**사전 조건**

```sql
INSERT INTO wallet (wallet_id, balance, created_at, updated_at)
VALUES ('test-wallet-b2', 100000, NOW(), NOW());
```

**입력**

| 항목 | 값 |
|------|----|
| walletId | `test-wallet-b2` |
| amount | 10,000원 |
| transactionId | `TXN_b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2` (전 스레드 동일) |
| 스레드 수 | 50 (동시) |

**기대 결과**

| 검증 항목 | 기대값 |
|-----------|--------|
| DB transaction 레코드 수 | **1건** (2중 저장 절대 불가) |
| 최종 잔액 | 90,000원 (1회만 차감) |
| 전체 응답 수 | 50건 모두 HTTP 200 |
| 모든 응답의 remainingBalance | 90,000원 (동일) |

**실제 결과**

| 검증 항목 | 실제값 | Pass/Fail |
|-----------|--------|-----------|
| DB transaction 레코드 수 | | ⬜ |
| 최종 잔액 | | ⬜ |
| 전체 HTTP 200 | | ⬜ |
| remainingBalance 일관성 | | ⬜ |

---

### B-3. 독립 transactionId 동시 요청 ★★☆

**목적** 각기 다른 transactionId를 사용하면 100건이 모두 독립적으로 처리되는가

**사전 조건**

```sql
INSERT INTO wallet (wallet_id, balance, created_at, updated_at)
VALUES ('test-wallet-b3', 1000000, NOW(), NOW());
```

**입력** 100 스레드 / 각기 다른 transactionId / 건당 10,000원

**기대 결과** A-1과 동일 (모두 성공, DB 레코드 100건, 총액 무결성)

**실제 결과**

| 검증 항목 | 실제값 | Pass/Fail |
|-----------|--------|-----------|
| DB transaction 레코드 수 | | ⬜ |
| finalBalance | | ⬜ |
| 총액 무결성 | | ⬜ |

---

## C. 날짜 범위 조회 테스트

> 클래스: `TransactionQueryTest`

---

### C-1. 정상 범위 조회 ★★★

**목적** 날짜 범위 필터링이 정확하게 동작하는가

**사전 조건**

```sql
-- 1월 데이터 10건 (withdrawal_date UTC 기준으로 삽입)
-- 2월 데이터 10건
-- 3월 데이터 10건
-- 총 30건 삽입
INSERT INTO transaction (transaction_id, wallet_id, withdrawal_amount, balance_after,
                         status, withdrawal_date)
VALUES
  ('TXN_jan01...', 'test-wallet-c', 1000, 99000, 'SUCCESS', '2025-12-31 15:00:00'),  -- KST 2026-01-01 00:00:00
  ('TXN_jan02...', 'test-wallet-c', 1000, 98000, 'SUCCESS', '2026-01-15 00:00:00'),  -- KST 2026-01-15 09:00:00
  ...
  ('TXN_feb01...', 'test-wallet-c', 1000, 89000, 'SUCCESS', '2026-01-31 15:00:00'),  -- KST 2026-02-01 00:00:00
  ...
```

**입력**

```
GET /api/v1/wallets/test-wallet-c/transactions
    ?startDate=2026-01-01T00:00:00+09:00
    &endDate=2026-01-31T23:59:59+09:00
    &page=0&size=20
```

**기대 결과**

| 검증 항목 | 기대값 |
|-----------|--------|
| 응답 건수 | 10건 (1월 데이터만) |
| 모든 withdrawalDate | 2026-01-01T00:00:00+09:00 ~ 2026-01-31T23:59:59+09:00 범위 내 |
| 2월·3월 데이터 포함 여부 | 미포함 |

**실제 결과**

| 검증 항목 | 실제값 | Pass/Fail |
|-----------|--------|-----------|
| 응답 건수 | | ⬜ |
| 범위 외 데이터 포함 여부 | | ⬜ |

---

### C-2. KST 자정 경계값 검증 ★★★

**목적** UTC 변환 없이 KST 값으로 쿼리할 경우 경계값 오류가 발생함을 증명하고,
올바른 UTC 변환으로 경계값이 정확히 처리되는가

**사전 조건**

```sql
-- TX_A: KST 2026-01-31 23:59:59 = UTC 2026-01-31 14:59:59
INSERT INTO transaction (..., withdrawal_date) VALUES (..., '2026-01-31 14:59:59');

-- TX_B: KST 2026-02-01 00:00:00 = UTC 2026-01-31 15:00:00
INSERT INTO transaction (..., withdrawal_date) VALUES (..., '2026-01-31 15:00:00');
```

**입력**

```
GET /api/v1/wallets/test-wallet-c2/transactions
    ?startDate=2026-01-01T00:00:00+09:00
    &endDate=2026-01-31T23:59:59+09:00
```

**기대 결과**

| 데이터 | 포함 여부 | 이유 |
|--------|----------|------|
| TX_A (KST 1월 31일 23:59:59) | **포함** | endDate 경계값 이내 |
| TX_B (KST 2월 1일 00:00:00) | **미포함** | endDate 경계값 초과 |

> UTC 변환 없이 KST 값으로 조회 시: TX_A의 UTC(14:59:59)가 KST endDate(23:59:59)보다
> 작아 포함되어야 하나, 변환 오류 시 TX_A가 누락될 수 있음

**실제 결과**

| 검증 항목 | 실제값 | Pass/Fail |
|-----------|--------|-----------|
| TX_A 포함 여부 | | ⬜ |
| TX_B 미포함 여부 | | ⬜ |

---

### C-3. startDate > endDate 거부 ★★☆

**입력**

```
?startDate=2026-03-01T00:00:00+09:00&endDate=2026-01-01T00:00:00+09:00
```

**기대 결과**

| 검증 항목 | 기대값 |
|-----------|--------|
| HTTP Status | 400 |
| errorCode | `INVALID_DATE_RANGE` |

**실제 결과**

| 검증 항목 | 실제값 | Pass/Fail |
|-----------|--------|-----------|
| HTTP Status | | ⬜ |
| errorCode | | ⬜ |

---

### C-4. 90일 초과 범위 거부 ★★☆

**입력**

```
?startDate=2026-01-01T00:00:00+09:00&endDate=2026-04-10T00:00:00+09:00  (100일)
```

**기대 결과**

| 검증 항목 | 기대값 |
|-----------|--------|
| HTTP Status | 400 |
| errorCode | `DATE_RANGE_EXCEEDED` |
| 응답 내 maxDays | 90 |

**실제 결과**

| 검증 항목 | 실제값 | Pass/Fail |
|-----------|--------|-----------|
| HTTP Status | | ⬜ |
| errorCode | | ⬜ |
| maxDays 포함 여부 | | ⬜ |

---

### C-5. 파라미터 없음 전체 조회 ★★☆

**입력** 날짜 파라미터 없이 `GET /transactions`

**기대 결과**

| 검증 항목 | 기대값 |
|-----------|--------|
| HTTP Status | 200 |
| totalElements | 전체 레코드 수와 일치 |
| 페이징 정상 동작 | page=0, size=20 기본값 적용 |

**실제 결과**

| 검증 항목 | 실제값 | Pass/Fail |
|-----------|--------|-----------|
| HTTP Status | | ⬜ |
| totalElements 일치 | | ⬜ |

---

### C-6. offset 없는 날짜 형식 거부 ★★☆

**목적** `"2026-01-01"` 형식은 timezone을 알 수 없으므로 서버가 명시적으로 거부해야 함

**입력**

```
?startDate=2026-01-01
```

**기대 결과**

| 검증 항목 | 기대값 |
|-----------|--------|
| HTTP Status | 400 |

**실제 결과**

| 검증 항목 | 실제값 | Pass/Fail |
|-----------|--------|-----------|
| HTTP Status | | ⬜ |

---

## 전체 케이스 요약

| 케이스 | 중요도 | 분류 | Pass/Fail |
|--------|--------|------|-----------|
| A-1 정상 소진 | ★★★ | 동시성 | ⬜ |
| A-2 Overdraft 방지 | ★★★ | 동시성 | ⬜ |
| A-3 락 제거 대조군 | ★★☆ | 동시성 | ⬜ |
| A-4 단일 순차 베이스라인 | ★☆☆ | 동시성 | ⬜ |
| B-1 순차 중복 차단 | ★★★ | 멱등성 | ⬜ |
| B-2 동시 중복 레이스 컨디션 | ★★★ | 멱등성 | ⬜ |
| B-3 독립 transactionId | ★★☆ | 멱등성 | ⬜ |
| C-1 정상 범위 조회 | ★★★ | 날짜 조회 | ⬜ |
| C-2 KST 자정 경계값 | ★★★ | 날짜 조회 | ⬜ |
| C-3 startDate > endDate | ★★☆ | 날짜 조회 | ⬜ |
| C-4 90일 초과 거부 | ★★☆ | 날짜 조회 | ⬜ |
| C-5 파라미터 없음 | ★★☆ | 날짜 조회 | ⬜ |
| C-6 offset 없는 형식 거부 | ★★☆ | 날짜 조회 | ⬜ |
