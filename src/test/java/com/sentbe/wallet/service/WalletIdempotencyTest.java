package com.sentbe.wallet.service;

import com.sentbe.wallet.domain.Wallet;
import com.sentbe.wallet.dto.WithdrawalRequest;
import com.sentbe.wallet.dto.WithdrawalResponse;
import com.sentbe.wallet.repository.TransactionRepository;
import com.sentbe.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WalletIdempotencyTest {

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private final String walletId = "idempotency-wallet";
    private final long initialBalance = 100_000L;
    private final long withdrawAmount = 10_000L;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();

        Wallet wallet = Wallet.builder()
                .walletId(walletId)
                .balance(initialBalance)
                .build();
        walletRepository.save(wallet);
    }

    @Test
    @DisplayName("B-1. 순차 중복 요청 차단: 동일 transactionId로 5회 순차 요청 시 1회만 처리되는지 확인")
    void sequentialIdempotencyTest() {
        // given
        String txnId = "TXN_" + UUID.randomUUID().toString().replace("-", "");
        WithdrawalRequest request = new WithdrawalRequest();
        request.setTransactionId(txnId);
        request.setAmount(withdrawAmount);

        // when
        for (int i = 1; i <= 5; i++) {
            WithdrawalResponse response = withdrawalService.withdraw(walletId, request);

            // then
            assertThat(response.getRemainingBalance()).isEqualTo(90_000L);
            if (i == 1) {
                assertThat(response.isIdempotent()).isFalse();
            } else {
                assertThat(response.isIdempotent()).isTrue();
            }
        }

        // final then
        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(90_000L);
        assertThat(transactionRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("B-2. 동시 중복 요청 레이스 컨디션: 동일 transactionId로 50개 스레드가 동시 요청 시 1회만 처리되는지 확인")
    void concurrentIdempotencyTest() throws InterruptedException {
        // given
        String txnId = "TXN_" + UUID.randomUUID().toString().replace("-", "");
        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    WithdrawalRequest request = new WithdrawalRequest();
                    request.setTransactionId(txnId);
                    request.setAmount(withdrawAmount);

                    withdrawalService.withdraw(walletId, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Ignore failures
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        // then
        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(90_000L); // 100,000 - 10,000 = 90,000
        assertThat(transactionRepository.count()).isEqualTo(1L);
        assertThat(successCount.get()).isEqualTo(threadCount); // 모든 요청이 200 OK (멱등 처리 포함)를 반환해야 함
    }

    @Test
    @DisplayName("B-3. 독립 transactionId 동시 요청: 100명이 각기 다른 transactionId로 동시에 요청 시 정상 처리되는지 확인")
    void independentConcurrentRequestsTest() throws InterruptedException {
        // given
        long initialLargeBalance = 1_000_000L;
        String largeWalletId = "large-wallet";
        Wallet wallet = Wallet.builder()
                .walletId(largeWalletId)
                .balance(initialLargeBalance)
                .build();
        walletRepository.save(wallet);

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    WithdrawalRequest request = new WithdrawalRequest();
                    request.setTransactionId("TXN_" + UUID.randomUUID().toString().replace("-", ""));
                    request.setAmount(withdrawAmount);

                    withdrawalService.withdraw(largeWalletId, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Ignore
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        // then
        Wallet finalWallet = walletRepository.findById(largeWalletId).orElseThrow();
        assertThat(finalWallet.getBalance()).isEqualTo(0L);
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(transactionRepository.findByWalletId(largeWalletId).size()).isEqualTo(threadCount);
    }
}
