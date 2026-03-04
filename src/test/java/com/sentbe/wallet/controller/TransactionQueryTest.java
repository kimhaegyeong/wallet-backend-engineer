package com.sentbe.wallet.controller;

import com.sentbe.wallet.domain.Transaction;
import com.sentbe.wallet.domain.TransactionStatus;
import com.sentbe.wallet.domain.Wallet;
import com.sentbe.wallet.repository.TransactionRepository;
import com.sentbe.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionQueryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private final String walletId = "query-test-wallet";

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();

        Wallet wallet = Wallet.builder()
                .walletId(walletId)
                .balance(100_000L)
                .build();
        walletRepository.save(wallet);
    }

    @Test
    @DisplayName("C-1. 정상 범위 조회: 1월 10건, 2월 10건, 3월 10건 중 1월 데이터만 10건 조회되는지 확인")
    void normalRangeQueryTest() throws Exception {
        // given
        List<Transaction> transactions = new ArrayList<>();
        // 1월 (UTC 2026-01-01 ~ 2026-01-31)
        for (int i = 0; i < 10; i++) {
            transactions
                    .add(createTransaction(walletId, OffsetDateTime.parse("2026-01-15T12:00:00+09:00").toInstant()));
        }
        // 2월
        for (int i = 0; i < 10; i++) {
            transactions
                    .add(createTransaction(walletId, OffsetDateTime.parse("2026-02-15T12:00:00+09:00").toInstant()));
        }
        // 3월
        for (int i = 0; i < 10; i++) {
            transactions
                    .add(createTransaction(walletId, OffsetDateTime.parse("2026-03-15T12:00:00+09:00").toInstant()));
        }
        transactionRepository.saveAll(transactions);

        // when & then
        mockMvc.perform(get("/api/v1/wallets/" + walletId + "/transactions")
                .param("startDate", "2026-01-01T00:00:00+09:00")
                .param("endDate", "2026-01-31T23:59:59+09:00")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactions", hasSize(10)))
                .andExpect(jsonPath("$.data.totalElements").value(10));
    }

    @Test
    @DisplayName("C-2. KST 자정 경계값 검증: 날짜 경계에 걸친 데이터가 정확히 포함되거나 제외되는지 확인")
    void kstBoundaryVerificationTest() throws Exception {
        // given
        // TX_A: 2026-01-31T23:59:59+09:00 (= UTC 14:59:59)
        Instant txAInstant = OffsetDateTime.parse("2026-01-31T23:59:59+09:00").toInstant();
        transactionRepository.save(createTransaction(walletId, txAInstant));

        // TX_B: 2026-02-01T00:00:00+09:00 (= UTC 15:00:00)
        Instant txBInstant = OffsetDateTime.parse("2026-02-01T00:00:00+09:00").toInstant();
        transactionRepository.save(createTransaction(walletId, txBInstant));

        // when & then (Request with endDate = 2026-01-31T23:59:59+09:00)
        mockMvc.perform(get("/api/v1/wallets/" + walletId + "/transactions")
                .param("endDate", "2026-01-31T23:59:59+09:00")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactions", hasSize(1)))
                .andExpect(jsonPath("$.data.transactions[0].withdrawalDate").value("2026-01-31T14:59:59Z")); // Jackson
                                                                                                             // default
                                                                                                             // serialization
                                                                                                             // is UTC
    }

    @Test
    @DisplayName("C-3. startDate > endDate 거부: 시작일이 종료일보다 뒤이면 400 에러 발생 확인")
    void invalidDateRangeFails() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/" + walletId + "/transactions")
                .param("startDate", "2026-03-01T00:00:00+09:00")
                .param("endDate", "2026-01-01T00:00:00+09:00")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("C-4. 90일 초과 범위 거부: 조회 기간이 90일을 넘으면 400 에러 발생 확인")
    void dateRangeExceededFails() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/" + walletId + "/transactions")
                .param("startDate", "2026-01-01T00:00:00+09:00")
                .param("endDate", "2026-04-10T00:00:00+09:00")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DATE_RANGE_EXCEEDED"));
    }

    @Test
    @DisplayName("C-5. 파라미터 없음 전체 조회: 날짜 필터링 없이 호출 시 모든 데이터가 조회되는지 확인")
    void getAllWithoutParameters() throws Exception {
        // given
        for (int i = 0; i < 5; i++) {
            transactionRepository.save(createTransaction(walletId, Instant.now()));
        }

        // when & then
        mockMvc.perform(get("/api/v1/wallets/" + walletId + "/transactions")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5));
    }

    @Test
    @DisplayName("C-6. offset 없는 날짜 형식 거부: timezone offset이 누락된 날짜 형식 입력 시 400 에러 발생 확인")
    void missingOffsetFails() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/" + walletId + "/transactions")
                .param("startDate", "2026-01-01")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }

    private Transaction createTransaction(String walletId, Instant withdrawalDate) {
        return Transaction.builder()
                .transactionId("TXN_" + UUID.randomUUID().toString().replace("-", ""))
                .walletId(walletId)
                .withdrawalAmount(1000L)
                .balanceAfter(99000L)
                .status(TransactionStatus.SUCCESS)
                .responseSnapshot("{}")
                .withdrawalDate(withdrawalDate)
                .build();
    }
}
