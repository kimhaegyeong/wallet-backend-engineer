# TASK.md — 개발 계획

> SentBe Backend Engineer 과제 개발 태스크 목록  
> 우선순위 순으로 구성, 각 태스크는 독립적으로 커밋 가능한 단위

---

## 진행 상태 범례

| 상태 | 의미 |
|------|------|
| `[ ]` | 미착수 |
| `[~]` | 진행 중 |
| `[x]` | 완료 |

---

## Phase 0 — 프로젝트 초기 세팅

> 목표: 로컬에서 빌드 및 Docker 기동이 되는 뼈대 구성

```
[x] 0-1. Spring Initializr로 프로젝트 생성
         - Spring Boot 3.x, Java 17, Gradle
         - 의존성: Spring Web, Spring Data JPA, MySQL Driver,
                   Flyway, Validation, Actuator, Lombok,
                   springdoc-openapi-starter-webmvc-ui:2.x

[x] 0-2. docker-compose.yml 작성
         - services: mysql:8.0, redis:7-alpine
         - mysql: 환경변수, 볼륨(init.sql 마운트), healthcheck
           * environment: TZ=UTC
           * command: --default-time-zone=UTC --character-set-server=utf8mb4
           ※ DB와 JVM timezone을 UTC로 일치시켜 JDBC 드라이버 시간 변환 오차 방지
         - redis: 포트 노출
         - app: depends_on(mysql healthy), 환경변수 주입

[x] 0-3. application.yml 작성
         - datasource: MySQL 연결 정보 (환경변수 참조)
           * url 파라미터: ?serverTimezone=UTC&useLegacyDatetimeCode=false
           ※ JDBC 드라이버가 timezone 변환 시 UTC 기준으로 동작하도록 명시
         - jpa: ddl-auto=validate, show-sql=true
           * properties.hibernate.jdbc.time_zone=UTC
           ※ Hibernate → DB 저장 시 UTC 강제
         - flyway: enabled=true, locations=classpath:db/migration
         - hikari: maximum-pool-size=20, connection-timeout=3000
         - jackson:
           * time-zone: Asia/Seoul   (API 응답 KST 변환)
           * date-format: yyyy-MM-dd'T'HH:mm:ssXXX  (offset 포함 ISO 8601)
           * serialization.write-dates-as-timestamps: false
         - app.timezone:
           * store: UTC              (DB 저장 기준 — 변경 금지)
           * display: Asia/Seoul     (API 응답 기준 — 리전 확장 시 이 값만 교체)
         - app.transaction:
           * max-date-range-days: 90  (날짜 범위 최대 조회 기간 — 운영 중 조정 가능)

[x] 0-4. .gitignore, README 초안 추가

[x] 0-5. 빌드 및 Docker 기동 확인
         ./gradlew build
         docker compose up -d
```

---

## Phase 1 — DB 마이그레이션 & 초기 데이터

> 목표: 테이블 자동 생성 및 테스트 데이터 삽입

```
[x] 1-1. Flyway DDL 작성: V1__init.sql
         - wallet 테이블 (wallet_id VARCHAR(32) PK, balance BIGINT,
                              created_at TIMESTAMP(6), updated_at TIMESTAMP(6))
           * wallet_id: UUID v4 하이픈 제거, 서버 자동 발급
           * DATETIME → TIMESTAMP: timezone 인식 타입, UTC 저장 보장
           * version 컬럼 미포함: 비관적 락 채택으로 낙관적 락 불필요
         - transaction 테이블 (id, transaction_id VARCHAR(36) UNIQUE, wallet_id VARCHAR(32),
           withdrawal_amount, balance_after, status, response_snapshot JSON,
           withdrawal_date TIMESTAMP(6))
           * transaction_id: 'TXN_' + UUID v4 하이픈 제거, 클라이언트 생성
           * withdrawal_date: TIMESTAMP(6) — UTC 저장, 응답 시 KST(+09:00) 변환
         - INDEX: idx_wallet_date (wallet_id, withdrawal_date)

[x] 1-2. 초기 데이터 삽입: V2__seed.sql
         - 550e8400e29b41d4a716446655440000: 잔액 1,000,000원  (동시성 테스트용)
         - 6ba7b8109dad11d180b400c04fd430c8: 잔액   500,000원  (일반 테스트용)
         - 6ba7b8119dad11d180b400c04fd430c8: 잔액         0원  (잔액 부족 테스트용)
         ※ README.md의 테스트용 ID 목록 참고

[x] 1-3. 애플리케이션 기동 후 테이블 생성 확인
         docker exec -it sentbe-mysql mysql -u root -proot wallet_db -e "SHOW TABLES;"
```

---

## Phase 2 — 도메인 엔티티 & 레포지토리

> 목표: JPA 엔티티와 레포지토리 레이어 구성

```
[x] 2-1. Wallet 엔티티 작성
         - @Entity, @Id
         - balance: Long 타입
         - createdAt, updatedAt: Instant 타입 (LocalDateTime 사용 금지)
           ※ LocalDateTime은 timezone 정보 없음 → JVM timezone에 완전 의존하는 버그 위험
         - withdraw(amount) 도메인 메서드 — 잔액 검증 및 차감 로직 내장
         ※ @Version 미사용: 비관적 락(SELECT FOR UPDATE)이 동시성을 완전히 보장하므로
            낙관적 락과의 혼용은 불필요한 중복 (UPDATE 시 version 조건절 오버헤드 발생)

[x] 2-2. Transaction 엔티티 작성
         - @Entity, transactionId: @Column(unique=true, length=36)
         - transactionId 형식 검증: @Pattern(regexp = "^TXN_[a-fA-F0-9]{32}$")
         - withdrawalDate: Instant 타입 (LocalDateTime 사용 금지)
           * DB: UTC TIMESTAMP 저장
           * API 응답: Jackson이 Asia/Seoul 기준으로 "2026-03-03T19:00:00+09:00" 직렬화
         - status: Enum (SUCCESS, FAILED, DUPLICATE)
         - responseSnapshot: JSON 컬럼 (String 또는 @Convert)

[x] 2-3. WalletRepository 작성
         - findByIdWithLock(walletId): @Lock(PESSIMISTIC_WRITE) + @Query
           "SELECT w FROM Wallet w WHERE w.walletId = :walletId"

[x] 2-4. TransactionRepository 작성
         - findByTransactionId(transactionId): 멱등 체크용
         - findByWalletIdAndWithdrawalDateBetween(walletId, startDate, endDate, Pageable): 날짜 범위 조회용
           * startDate, endDate 타입: Instant (UTC 변환 후 전달)
           * startDate / endDate null 허용 → 조건 동적 처리 (QueryDSL 또는 Specification 활용)
           * INDEX idx_wallet_date (wallet_id, withdrawal_date) 활용 확인
```

---

## Phase 3 — 서비스 레이어 (핵심 비즈니스 로직)

> 목표: 동시성 제어 + 멱등성 보장 구현

```
[x] 3-1. WithdrawalService.withdraw() 구현
         트랜잭션 경계:
           @Transactional (전체를 하나의 트랜잭션으로 묶어 락 범위 보장)

         처리 순서:
           1. transactionRepository.findByTransactionId() 멱등 선조회
              → 존재하면: response_snapshot 기반으로 즉시 반환 (잔액 미변경)
           2. walletRepository.findByIdWithLock() — SELECT FOR UPDATE
              → 없으면: WalletNotFoundException
           3. wallet.withdraw(amount) — 잔액 검증 및 차감
              → 잔액 부족: InsufficientBalanceException
           4. transactionRepository.save() — 내역 저장
              → DuplicateKeyException 발생 시: catch → 멱등 응답 반환 (2차 방어)
           5. 결과 반환

[x] 3-2. WalletQueryService.getTransactions() 구현
         처리 순서:
           1. Wallet 존재 여부 선검증 (없으면 WalletNotFoundException)
           2. 날짜 파라미터 검증
              - startDate > endDate → InvalidDateRangeException (400)
              - 범위 > max-date-range-days(90일) → DateRangeExceededException (400)
           3. KST → UTC 변환
              OffsetDateTime.parse(startDate).toInstant()
              ※ DB는 UTC 저장이므로 변환 없이 조회하면 9시간 오차 발생
           4. transactionRepository 날짜 범위 조회 (Instant 기준)
           5. Page<Transaction> → TransactionListResponse 변환 (응답은 KST 직렬화)

[x] 3-3. 예외 클래스 정의
         - WalletNotFoundException (404)
         - InsufficientBalanceException (422) — currentBalance, requestedAmount 포함
         - InvalidAmountException (400)
         - InvalidDateRangeException (400) — startDate > endDate
         - DateRangeExceededException (400) — 조회 범위 90일 초과, maxDays 포함
         - LockAcquisitionTimeoutException (503)
```

---

## Phase 4 — API 레이어 (Controller & DTO)

> 목표: RESTful 엔드포인트 노출

```
[x] 4-1. 공통 응답 래퍼 작성
         - ApiResponse<T> { success, idempotent, data, error }
         - ErrorResponse { code, message, ... }

[x] 4-2. WithdrawalController 작성
         POST /api/v1/wallets/{walletId}/withdrawals
         - @PathVariable walletId
         - @RequestBody @Valid WithdrawalRequest
         - WithdrawalService.withdraw() 호출 및 응답 반환

[x] 4-3. TransactionController 작성
         GET /api/v1/wallets/{walletId}/transactions
         - @PathVariable walletId
         - @RequestParam(required=false) String startDate  (ISO 8601 + offset)
         - @RequestParam(required=false) String endDate    (ISO 8601 + offset)
         - @PageableDefault(size=20, sort="withdrawalDate", direction=DESC) Pageable
         - WalletQueryService.getTransactions(walletId, startDate, endDate, pageable) 호출

[x] 4-4. GlobalExceptionHandler 작성 (@RestControllerAdvice)
         - WalletNotFoundException → 404
         - InsufficientBalanceException → 422
         - InvalidAmountException → 400
         - MethodArgumentNotValidException → 400 (Bean Validation 실패)
         - DataIntegrityViolationException → 멱등 응답 200 (DuplicateKey 처리)
         - InvalidDateRangeException → 400 + INVALID_DATE_RANGE
         - DateRangeExceededException → 400 + DATE_RANGE_EXCEEDED
         - LockAcquisitionTimeoutException → 503
         - Exception → 500

[ ] 4-5. Request DTO 검증 (@Valid)
         - amount: @NotNull, @Min(1) "출금액은 1원 이상이어야 합니다."
         - transactionId: @NotBlank, @Pattern(regexp = "^TXN_[a-fA-F0-9]{32}$")
           형식 오류 시 400 + INVALID_TRANSACTION_ID_FORMAT 반환
         - startDate / endDate: ISO 8601 + offset 형식 파싱 실패 시 400 반환
           OffsetDateTime.parse() 실패 → DateTimeParseException → 400 처리
           예시: "2026-01-01T00:00:00+09:00" (정상), "2026-01-01" (오류)

[ ] 4-6. TimezoneConfig.java 작성
         - @Configuration
         - @Value("${app.timezone.display}") displayTimezone 주입
         - Bean: Jackson2ObjectMapperBuilderCustomizer
           → builder.timeZone(displayTimezone) 으로 응답 직렬화 timezone 주입
         - Bean: ZoneId displayZoneId()
           → 서비스 레이어에서 ZoneId.of("Asia/Seoul") 하드코딩 대신 주입받아 사용
         - WalletApplication.java static 블록에 TimeZone.setDefault(UTC) 추가
           ※ JVM 전역 UTC 고정 — OS timezone 설정 의존 제거

[ ] 4-7. Swagger (SpringDoc OpenAPI 3) 설정
         - SwaggerConfig.java 작성
           * @OpenAPIDefinition — title, version, description 기재
           * @SecurityScheme — 향후 인증 확장 대비 (현재 과제는 미적용)
         - application.yml 설정 추가
           springdoc.swagger-ui.path=/swagger-ui/index.html
           springdoc.api-docs.path=/api-docs
         - Controller 어노테이션 추가
           * @Tag(name = "Wallet", description = "월렛 출금 및 거래내역 API")
           * @Operation(summary = ..., description = ...)
           * @ApiResponse(responseCode = ..., description = ...)
         - DTO 어노테이션 추가
           * @Schema(description = ..., example = ...)
         - 접속 URL 확인: http://localhost:8080/swagger-ui/index.html
```

---

## Phase 5 — 통합 테스트 작성

> 목표: 동시성 제어 · 멱등성 · 날짜 범위 조회가 실제로 올바르게 동작함을 통합 테스트로 입증
> 공통 설정: @SpringBootTest + @Transactional 미사용 (실제 커밋이 되어야 동시성 테스트 유효)
> 상세 시나리오: TEST_CASES.md 참조

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 A. 동시성 제어 테스트 (WalletConcurrencyTest)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[ ] A-1. 정상 소진 시나리오 ★★★ 필수
         - 초기 잔액: 1,000,000원 / 100 스레드 × 10,000원
         - CountDownLatch(1) startLatch — 일제 출발 보장
         - CountDownLatch(100) doneLatch — 전체 완료 대기
         - 검증:
           * finalBalance == 0
           * successCount == 100
           * failCount == 0
           * DB transaction 레코드 수 == 100
           * successCount * 10,000 + finalBalance == 1,000,000

[ ] A-2. Overdraft 방지 시나리오 ★★★ 필수
         - 초기 잔액: 50,000원 / 100 스레드 × 10,000원 (요청 총액 > 잔액)
         - 검증:
           * finalBalance == 0                    (정확히 소진, 절대 음수 불가)
           * successCount == 5
           * failCount == 95                      (INSUFFICIENT_BALANCE 95건)
           * DB transaction 레코드 수 == 100      (성공 + 실패 모두 기록)
           * successCount * 10,000 + finalBalance == 50,000

[ ] A-3. 락 제거 대조군 실험 ★★☆ 권장
         - A-2와 동일 조건, SELECT FOR UPDATE 제거 후 실행
         - 기대: finalBalance < 0 또는 successCount > 5 발생
         - 목적: 락이 없으면 버그가 발생함을 증명 → 제어 적용의 필요성 입증
         - 결과 캡처 → TEST_CASES.md A-3 결과란 기재

[ ] A-4. 단일 순차 베이스라인 ★☆☆ 선택
         - 초기 잔액: 100,000원 / 1 스레드 / 10회 순차 출금 / 건당 10,000원
         - 검증:
           * 매 출금 후 잔액 10,000원씩 감소
           * 10회차 후 finalBalance == 0
           * DB 레코드 순서와 balance_after 값 일치

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 B. 멱등성 테스트 (WalletIdempotencyTest)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[ ] B-1. 순차 중복 요청 차단 ★★★ 필수
         - 초기 잔액: 100,000원 / 동일 transactionId / 5회 순차 요청
         - 검증:
           * 1회차: 성공, balance = 90,000원
           * 2~5회차: idempotent=true, balance 변경 없음 (90,000원 유지)
           * DB transaction 레코드 수 == 1건
           * 모든 응답의 remainingBalance == 90,000원

[ ] B-2. 동시 중복 요청 레이스 컨디션 ★★★ 필수
         - 초기 잔액: 100,000원 / 동일 transactionId / 50 스레드 동시 요청
         - 검증:
           * DB transaction 레코드 수 == 1건          (2중 저장 절대 불가)
           * 최종 잔액 == 90,000원                    (1회만 차감)
           * 모든 응답 200 반환 (50건)
           * 모든 응답의 remainingBalance 동일
         ※ 2차 방어(DB UNIQUE → DuplicateKeyException catch) 동작 검증

[ ] B-3. 독립 transactionId 동시 요청 ★★☆ 권장
         - 초기 잔액: 1,000,000원 / 100 스레드 / 각기 다른 transactionId
         - 검증: A-1과 동일한 잔액 무결성 + DB 레코드 100건 (각각 독립 처리)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 C. 날짜 범위 조회 테스트 (TransactionQueryTest)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[ ] C-1. 정상 범위 조회 ★★★ 필수
         - 사전 조건: 1월 10건, 2월 10건, 3월 10건 삽입 (총 30건)
         - 요청: startDate=2026-01-01T00:00:00+09:00 ~ endDate=2026-01-31T23:59:59+09:00
         - 검증:
           * 응답 건수 == 10건 (1월 데이터만)
           * 모든 withdrawalDate가 요청 범위 내 KST 값

[ ] C-2. KST 자정 경계값 검증 ★★★ 필수
         - 사전 조건:
           TX_A: 2026-01-31T23:59:59+09:00 (= UTC 14:59:59) 삽입
           TX_B: 2026-02-01T00:00:00+09:00 (= UTC 15:00:00) 삽입
         - 요청: endDate=2026-01-31T23:59:59+09:00
         - 검증:
           * TX_A 포함 (경계값 포함)
           * TX_B 미포함 (다음 날 0시 제외)
         ※ UTC 변환 없이 KST 값으로 쿼리하면 TX_A 누락 버그 발생
            이 테스트 통과 = timezone 변환 정확성 증명

[ ] C-3. startDate > endDate 거부 ★★☆ 권장
         - 요청: startDate=2026-03-01 / endDate=2026-01-01
         - 검증: HTTP 400 + INVALID_DATE_RANGE

[ ] C-4. 90일 초과 범위 거부 ★★☆ 권장
         - 요청: startDate=2026-01-01 ~ endDate=2026-04-10 (100일)
         - 검증: HTTP 400 + DATE_RANGE_EXCEEDED + maxDays=90 포함 확인

[ ] C-5. 파라미터 없음 전체 조회 ★★☆ 권장
         - 요청: 날짜 파라미터 없이 GET /transactions
         - 검증: HTTP 200 + totalElements == 전체 레코드 수

[ ] C-6. offset 없는 날짜 형식 거부 ★★☆ 권장
         - 요청: startDate=2026-01-01 (offset 없음)
         - 검증: HTTP 400
         ※ KST인지 UTC인지 서버가 판단 불가 → 명시적 거부

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 D. 마무리
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[ ] D-1. 전체 테스트 실행 및 결과 기록
         ./gradlew test
         결과 → TEST_CASES.md 각 케이스 결과란 기재

[ ] D-2. TEST_RESULTS.md 작성
         - 동시성 테스트 실행 로그 / 캡처 첨부
         - A-3 락 제거 전/후 비교표
         - 전체 케이스 Pass/Fail 요약
```

---

## Phase 6 — 마무리 및 문서화

> 목표: 리뷰어가 즉시 실행 및 검증 가능한 상태

```
[ ] 6-1. docker-compose.yml 최종 검증
         docker compose down -v && docker compose up -d
         ./gradlew bootRun 실행 확인

[ ] 6-2. README.md 최종 보완
         - 실행 방법 전체 흐름 재검토
         - 테스트 결과 섹션 실제 수치로 업데이트
         - Swagger UI 접속 URL 명시 확인
         - Timezone 전략 섹션 내용 확인 (UTC 저장 / KST 응답 근거 기재)

[ ] 6-3. TEST_CASES.md 작성 (별도 문서)
         - 전체 케이스 A-1 ~ C-6 상세 명세
         - 각 케이스별 사전 조건 / 입력 / 기대 결과 / 실제 결과 기재

[ ] 6-4. TEST_RESULTS.md 작성
         - 전체 Pass/Fail 요약표
         - A-3 락 제거 전/후 비교 캡처
         - 동시성 테스트 실행 로그 첨부

[ ] 6-5. 코드 정리
         - 불필요한 주석, TODO 제거
         - 패키지 구조 최종 확인

[ ] 6-6. 최종 빌드 및 테스트 전체 통과 확인
         ./gradlew clean build
```

---

## 태스크 의존 관계 요약

```
Phase 0 (환경 세팅)
    │
    ▼
Phase 1 (DB 마이그레이션)
    │
    ▼
Phase 2 (엔티티 & 레포지토리)
    │
    ├──────────────────────┐
    ▼                      ▼
Phase 3 (서비스)       Phase 4 (Controller)
    │                      │
    └──────────┬───────────┘
               ▼
          Phase 5 (테스트)
               │
               ▼
          Phase 6 (마무리)
```

## 체크리스트 — 제출 전 최종 확인

```
[ ] docker compose up -d 후 ./gradlew bootRun 이 오류 없이 실행됨
[ ] POST /api/v1/wallets/{walletId}/withdrawals 정상 동작
[ ] GET  /api/v1/wallets/{walletId}/transactions 정상 동작 (날짜 파라미터 없음)
[ ] GET  /api/v1/wallets/{walletId}/transactions?startDate=...&endDate=... 날짜 범위 조회 정상 동작
[ ] startDate > endDate 요청 시 400 + INVALID_DATE_RANGE 반환
[ ] 90일 초과 범위 요청 시 400 + DATE_RANGE_EXCEEDED 반환
[ ] offset 없는 날짜 형식("2026-01-01") 입력 시 400 반환
[ ] 잔액 부족 시 422 + INSUFFICIENT_BALANCE 반환
[ ] 동일 transactionId 재요청 시 200 + idempotent: true 반환
[ ] transactionId 형식 오류(TXN_ 미포함 등) 시 400 반환
[ ] API 응답 withdrawalDate가 "2026-xx-xxT??:??:??+09:00" 형식(KST offset 포함)으로 반환됨
[ ] DB withdrawal_date 컬럼이 UTC로 저장됨 (MySQL: SELECT UTC_TIMESTAMP() 기준 확인)
[ ] http://localhost:8080/swagger-ui/index.html 접속 및 API 목록 노출 확인
[ ] A-1 정상 소진: successCount==100, finalBalance==0, 총액 무결성
[ ] A-2 Overdraft 방지: successCount==5, finalBalance==0, failCount==95
[ ] A-3 락 제거 대조군: 버그 발생 확인 (음수 잔액 또는 초과 성공)
[ ] B-1 순차 중복: DB 레코드 1건, 잔액 1회만 차감
[ ] B-2 동시 중복: DB 레코드 1건, 50건 모두 200 응답
[ ] C-1 날짜 범위 필터링: 1월 데이터 10건만 반환
[ ] C-2 KST 자정 경계값: TX_A 포함, TX_B 미포함 정확히 구분
[ ] C-3~C-6 날짜 파라미터 검증 케이스 전체 통과
[ ] TEST_CASES.md 전 케이스 결과란 기재 완료
[ ] README.md 실행 방법, 설계 결정, 테스트 결과 모두 기재됨
[ ] ./gradlew clean build 성공
```
