package com.sentbe.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentbe.wallet.domain.Transaction;
import com.sentbe.wallet.domain.TransactionStatus;
import com.sentbe.wallet.domain.Wallet;
import com.sentbe.wallet.dto.WithdrawalRequest;
import com.sentbe.wallet.dto.WithdrawalResponse;
import com.sentbe.wallet.exception.InsufficientBalanceException;
import com.sentbe.wallet.exception.WalletNotFoundException;
import com.sentbe.wallet.repository.TransactionRepository;
import com.sentbe.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    /**
     * 출금 프로세스
     *
     * @param walletId 월렛 ID
     * @param request  출금 요청 정보
     * @return 출금 결과 응답
     */
    @Transactional
    public WithdrawalResponse withdraw(String walletId, WithdrawalRequest request) {
        // 1. 멱등성 선조회
        var existingTransaction = transactionRepository.findByTransactionId(request.getTransactionId());
        if (existingTransaction.isPresent()) {
            return convertToIdempotentResponse(existingTransaction.get());
        }

        // 2. 비관적 락 조회
        Wallet wallet = walletRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        // 3. 차감
        try {
            wallet.withdraw(request.getAmount());
        } catch (IllegalStateException e) {
            throw new InsufficientBalanceException(wallet.getBalance(), request.getAmount());
        }

        // 4. 저장 및 결과 구성
        WithdrawalResponse response = buildResponse(walletId, request, wallet.getBalance(), TransactionStatus.SUCCESS,
                false);

        Transaction transaction = Transaction.builder()
                .transactionId(request.getTransactionId())
                .walletId(walletId)
                .withdrawalAmount(request.getAmount())
                .balanceAfter(wallet.getBalance())
                .status(TransactionStatus.SUCCESS)
                .responseSnapshot(toJson(response))
                .withdrawalDate(Instant.now())
                .build();

        try {
            transactionRepository.save(transaction);
        } catch (DataIntegrityViolationException e) {
            log.warn("DuplicateKeyException (DataIntegrityViolation) caught for transactionId: {}",
                    request.getTransactionId());
            return transactionRepository.findByTransactionId(request.getTransactionId())
                    .map(this::convertToIdempotentResponse)
                    .orElseThrow(() -> e); // 재조회 실패 시 원본 예외 투척
        }

        return response;
    }

    private WithdrawalResponse convertToIdempotentResponse(Transaction transaction) {
        try {
            WithdrawalResponse response = objectMapper.readValue(transaction.getResponseSnapshot(),
                    WithdrawalResponse.class);
            return WithdrawalResponse.builder()
                    .transactionId(response.getTransactionId())
                    .walletId(response.getWalletId())
                    .withdrawalAmount(response.getWithdrawalAmount())
                    .remainingBalance(response.getRemainingBalance())
                    .status(response.getStatus())
                    .withdrawalDate(response.getWithdrawalDate())
                    .idempotent(true) // 멱등 처리되었음을 표시
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse response snapshot for transaction: {}", transaction.getTransactionId(), e);
            throw new RuntimeException("Data corruption: failed to parse idempotent response", e);
        }
    }

    private WithdrawalResponse buildResponse(String walletId, WithdrawalRequest request, long remainingBalance,
            TransactionStatus status, boolean idempotent) {
        return WithdrawalResponse.builder()
                .transactionId(request.getTransactionId())
                .walletId(walletId)
                .withdrawalAmount(request.getAmount())
                .remainingBalance(remainingBalance)
                .status(status)
                .withdrawalDate(Instant.now())
                .idempotent(idempotent)
                .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize response snapshot", e);
        }
    }
}
