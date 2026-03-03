package com.sentbe.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentbe.wallet.domain.Transaction;
import com.sentbe.wallet.dto.TransactionListResponse;
import com.sentbe.wallet.dto.WithdrawalResponse;
import com.sentbe.wallet.exception.DateRangeExceededException;
import com.sentbe.wallet.exception.InvalidDateRangeException;
import com.sentbe.wallet.exception.WalletNotFoundException;
import com.sentbe.wallet.repository.TransactionRepository;
import com.sentbe.wallet.repository.TransactionSpecification;
import com.sentbe.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletQueryService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.transaction.max-date-range-days:90}")
    private int maxDateRangeDays;

    /**
     * 월렛의 거래 내역을 조회합니다.
     *
     * @param walletId  월렛 ID
     * @param startDate 시작일 (ISO 8601 + Offset)
     * @param endDate   종료일 (ISO 8601 + Offset)
     * @param pageable  페이징 정보
     * @return 페이징된 거래 내역
     */
    @Transactional(readOnly = true)
    public TransactionListResponse getTransactions(String walletId, String startDate, String endDate,
            Pageable pageable) {
        // 1. 월렛 존재 여부 검증
        if (!walletRepository.existsById(walletId)) {
            throw new WalletNotFoundException(walletId);
        }

        // 2. 날짜 파라미터 파싱 및 검증
        Instant startInstant = (startDate != null) ? OffsetDateTime.parse(startDate).toInstant() : null;
        Instant endInstant = (endDate != null) ? OffsetDateTime.parse(endDate).toInstant() : null;

        validateDateRange(startInstant, endInstant);

        // 3. 조회
        var spec = TransactionSpecification.withWalletAndDateRange(walletId, startInstant, endInstant);
        Page<Transaction> transactionPage = transactionRepository.findAll(spec, pageable);

        // 4. 변환 및 반환
        return TransactionListResponse.builder()
                .transactions(transactionPage.map(this::convertToResponse).getContent())
                .totalElements(transactionPage.getTotalElements())
                .totalPages(transactionPage.getTotalPages())
                .currentPage(transactionPage.getNumber())
                .size(transactionPage.getSize())
                .build();
    }

    private void validateDateRange(Instant start, Instant end) {
        if (start == null || end == null) {
            return;
        }

        if (start.isAfter(end)) {
            throw new InvalidDateRangeException();
        }

        long daysBetween = ChronoUnit.DAYS.between(start, end);
        if (daysBetween > maxDateRangeDays) {
            throw new DateRangeExceededException(maxDateRangeDays);
        }
    }

    private WithdrawalResponse convertToResponse(Transaction transaction) {
        try {
            // response_snapshot에 저장된 원본 응답 정보를 활용하거나 필드 직접 매핑
            // 여기서는 snapshot을 파싱하여 기본 정보를 구성함
            WithdrawalResponse snapshot = objectMapper.readValue(transaction.getResponseSnapshot(),
                    WithdrawalResponse.class);
            return WithdrawalResponse.builder()
                    .transactionId(transaction.getTransactionId())
                    .walletId(transaction.getWalletId())
                    .withdrawalAmount(transaction.getWithdrawalAmount())
                    .remainingBalance(transaction.getBalanceAfter())
                    .status(transaction.getStatus())
                    .withdrawalDate(transaction.getWithdrawalDate())
                    .idempotent(false) // 조회 시에는 멱등 플래그 미의미
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse transaction snapshot: {}", transaction.getTransactionId(), e);
            // Snapshot 파싱 실패 시 기본 필드로 구성
            return WithdrawalResponse.builder()
                    .transactionId(transaction.getTransactionId())
                    .walletId(transaction.getWalletId())
                    .withdrawalAmount(transaction.getWithdrawalAmount())
                    .remainingBalance(transaction.getBalanceAfter())
                    .status(transaction.getStatus())
                    .withdrawalDate(transaction.getWithdrawalDate())
                    .build();
        }
    }
}
