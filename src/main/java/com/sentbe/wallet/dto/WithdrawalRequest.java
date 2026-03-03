package com.sentbe.wallet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 출금 요청 DTO
 */
@Getter
@Setter
public class WithdrawalRequest {

    @NotBlank(message = "Transaction ID is required.")
    @Pattern(regexp = "^TXN_[a-fA-F0-9]{32}$", message = "Invalid transaction ID format.")
    private String transactionId;

    @NotNull(message = "Withdrawal amount is required.")
    @Min(value = 1, message = "Withdrawal amount must be at least 1.")
    private Long amount;
}
