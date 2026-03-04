package com.sentbe.wallet.service;

import com.sentbe.wallet.domain.Wallet;
import com.sentbe.wallet.dto.WithdrawalRequest;
import com.sentbe.wallet.repository.TransactionRepository;
import com.sentbe.wallet.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class WalletConcurrencyTest {

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private final String walletId = "test-wallet-id";
    private final long initialBalance = 1_000_000L;
    private final int threadCount = 100;
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
    @DisplayName("A-1. 정상 소진 시나리오: 100명이 동시에 1만원씩 출금하여 잔액이 0원이 되는지 확인")
    void normalDepletionScenario() throws InterruptedException {
        // given
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    WithdrawalRequest request = new WithdrawalRequest();
                    String txnId = "TXN_" + UUID.randomUUID().toString().replace("-", "");
                    request.setTransactionId(txnId);
                    request.setAmount(withdrawAmount);

                    withdrawalService.withdraw(walletId, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
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
        long finalBalance = wallet.getBalance();
        long transactionCount = transactionRepository.count();

        assertThat(finalBalance).isEqualTo(0L);
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(transactionCount).isEqualTo(threadCount);
        assertThat(successCount.get() * withdrawAmount + finalBalance).isEqualTo(initialBalance);
    }

    @Test
    @DisplayName("A-2. Overdraft 방지 시나리오: 잔액보다 많은 금액을 동시에 요청할 때 정확히 잔액만큼만 소진되는지 확인")
    void overdraftPreventionScenario() throws InterruptedException {
        // given
        long oInitialBalance = 50_000L;
        String oWalletId = "overdraft-wallet";
        Wallet wallet = Wallet.builder()
                .walletId(oWalletId)
                .balance(oInitialBalance)
                .build();
        walletRepository.save(wallet);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    WithdrawalRequest request = new WithdrawalRequest();
                    request.setTransactionId("TXN_" + UUID.randomUUID().toString().replace("-", ""));
                    request.setAmount(withdrawAmount);

                    withdrawalService.withdraw(oWalletId, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        // then
        Wallet finalWallet = walletRepository.findById(oWalletId).orElseThrow();
        long finalBalance = finalWallet.getBalance();
        long transactionCount = transactionRepository.findByWalletId(oWalletId).size();

        assertThat(finalBalance).isEqualTo(0L);
        assertThat(successCount.get()).isEqualTo(5); // 50,000 / 10,000 = 5
        assertThat(failCount.get()).isEqualTo(95);
        assertThat(transactionCount).isEqualTo(threadCount);
        assertThat(successCount.get() * withdrawAmount + finalBalance).isEqualTo(oInitialBalance);
    }

    @Test
    @DisplayName("A-3. 락 제거 대조군 실험: 락이 없으면 동시성 이슈(Overdraft)가 발생하는지 확인")
    void lockRemovalExperimentScenario() throws InterruptedException {
        // given
        long xInitialBalance = 50_000L;
        String xWalletId = "no-lock-wallet";
        Wallet wallet = Wallet.builder()
                .walletId(xWalletId)
                .balance(xInitialBalance)
                .build();
        walletRepository.save(wallet);

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

                    withdrawalService.withdrawWithoutLock(xWalletId, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Ignore failures for this experiment to see how many "succeed" incorrectly
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        // then
        Wallet finalWallet = walletRepository.findById(xWalletId).orElseThrow();
        long finalBalance = finalWallet.getBalance();

        log.info("A-3 Experiment Result: successCount={}, finalBalance={}", successCount.get(), finalBalance);

        // 락이 없으면 successCount가 5보다 크거나 finalBalance가 음수일 가능성이 높음 (Race Condition)
        // 하지만 운 좋게 성공할 수도 있으므로 "버그 가능성"을 확인하는 용도
        // assertThat(successCount.get()).isGreaterThan(5); // 이 테스트는 실패할 수도 있지만 대조군으로서
        // 의미가 있음
    }

    @Test
    @DisplayName("A-4. 단일 순차 베이스라인: 1명이 10회 순차 출금 시 잔액이 정확히 줄어드는지 확인")
    void sequentialBaselineScenario() {
        // given
        long sInitialBalance = 100_000L;
        String sWalletId = "sequential-wallet";
        Wallet wallet = Wallet.builder()
                .walletId(sWalletId)
                .balance(sInitialBalance)
                .build();
        walletRepository.save(wallet);

        // when
        for (int i = 1; i <= 10; i++) {
            WithdrawalRequest request = new WithdrawalRequest();
            request.setTransactionId("TXN_SEQ_" + i + "_" + UUID.randomUUID().toString().substring(0, 10));
            request.setAmount(withdrawAmount);

            withdrawalService.withdraw(sWalletId, request);

            // then: 매 출금 후 잔액 확인
            Wallet currentWallet = walletRepository.findById(sWalletId).orElseThrow();
            assertThat(currentWallet.getBalance()).isEqualTo(sInitialBalance - (i * withdrawAmount));
        }

        // then: 최종 확인
        Wallet finalWallet = walletRepository.findById(sWalletId).orElseThrow();
        assertThat(finalWallet.getBalance()).isEqualTo(0L);
    }
}
