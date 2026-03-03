package com.sentbe.wallet.dto;

import com.sentbe.wallet.domain.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 출금 처리 결과를 담는 DTO (response_snapshot에 저장됨)
 */
@Getter
@Builder
@Schema(description = "출금 처리 결과 정보")
public class WithdrawalResponse {
    @Schema(description = "트랜잭션 ID", example = "TXN_7293ced496e5454687504f4fa3347c4d")
    private String transactionId;

    @Schema(description = "월렛 ID", example = "550e8400e29b41d4a716446655440000")
    private String walletId;

    @Schema(description = "출금 금액", example = "5000")
    private Long withdrawalAmount;

    @Schema(description = "출금 후 잔액", example = "95000")
    private Long remainingBalance;

    @Schema(description = "거래 상태", example = "SUCCESS")
    private TransactionStatus status;

    @Schema(description = "출금 일시 (ISO 8601)", example = "2026-01-01T09:00:00+09:00")
    private Instant withdrawalDate;

    @Schema(description = "멱등 요청 결과 여부", example = "false")
    private boolean idempotent;
}
