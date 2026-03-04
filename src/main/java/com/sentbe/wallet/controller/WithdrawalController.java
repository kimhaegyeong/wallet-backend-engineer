package com.sentbe.wallet.controller;

import com.sentbe.wallet.dto.ApiResponse;
import com.sentbe.wallet.dto.WithdrawalRequest;
import com.sentbe.wallet.dto.WithdrawalResponse;
import com.sentbe.wallet.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 출금 API 컨트롤러
 */
@Tag(name = "Wallet", description = "월렛 출금 및 거래내역 API")
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    /**
     * 출금 요청 처리
     *
     * @param walletId 월렛 ID
     * @param request  출금 요청 정보
     * @return 출금 결과 응답
     */
    @Operation(summary = "출금", description = "입력받은 금액만큼 월렛에서 출금합니다. transactionId를 통한 멱등성을 보장합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "출금 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터 또는 트랜잭션 ID 형식 오류", content = @Content(schema = @Schema(implementation = com.sentbe.wallet.dto.ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "잔액 부족", content = @Content(schema = @Schema(implementation = com.sentbe.wallet.dto.ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "시스템 일시 부하 (락 획득 타임아웃)", content = @Content(schema = @Schema(implementation = com.sentbe.wallet.dto.ApiResponse.class)))
    @PostMapping("/{walletId}/withdrawals")
    public ApiResponse<WithdrawalResponse> withdraw(
            @PathVariable String walletId,
            @RequestBody @Valid WithdrawalRequest request) {

        WithdrawalResponse response = withdrawalService.withdraw(walletId, request);

        return ApiResponse.success(response, response.isIdempotent());
    }
}
