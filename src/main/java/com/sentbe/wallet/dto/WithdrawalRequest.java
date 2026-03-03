package com.sentbe.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "출금 요청 정보")
public class WithdrawalRequest {

    @NotBlank(message = "트랜잭션 ID는 필수입니다.")
    @Pattern(regexp = "^TXN_[a-fA-F0-9]{32}$", message = "트랜잭션 ID 형식이 유효하지 않습니다.")
    @Schema(description = "트랜잭션 ID (UUID v4 32자리, TXN_ 접두어 포함)", example = "TXN_7293ced496e5454687504f4fa3347c4d")
    private String transactionId;

    @NotNull(message = "출금 금액은 필수입니다.")
    @Min(value = 1, message = "출금 금액은 1원 이상이어야 합니다.")
    @Schema(description = "출금 금액", example = "5000")
    private Long amount;
}
