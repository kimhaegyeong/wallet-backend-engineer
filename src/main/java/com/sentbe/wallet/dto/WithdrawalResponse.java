package com.sentbe.wallet.dto;

import com.sentbe.wallet.domain.TransactionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 출금 처리 결과를 담는 DTO (response_snapshot에 저장됨)
 */
@Getter
@Builder
public class WithdrawalResponse {
    private String transactionId;
    private String walletId;
    private Long withdrawalAmount;
    private Long remainingBalance;
    private TransactionStatus status;
    private Instant withdrawalDate;
    private boolean idempotent;
}
