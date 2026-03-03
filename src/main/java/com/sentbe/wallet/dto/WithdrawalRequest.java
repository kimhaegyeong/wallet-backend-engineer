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

    @NotBlank(message = "트랜잭션 ID는 필수입니다.")
    @Pattern(regexp = "^TXN_[a-fA-F0-9]{32}$", message = "트랜잭션 ID 형식이 유효하지 않습니다.")
    private String transactionId;

    @NotNull(message = "출금 금액은 필수입니다.")
    @Min(value = 1, message = "출금 금액은 1원 이상이어야 합니다.")
    private Long amount;
}
