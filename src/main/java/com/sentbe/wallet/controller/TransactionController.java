package com.sentbe.wallet.controller;

import com.sentbe.wallet.dto.ApiResponse;
import com.sentbe.wallet.dto.TransactionListResponse;
import com.sentbe.wallet.service.WalletQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 거래 내역 조회 API 컨트롤러
 */
@Tag(name = "Wallet", description = "월렛 출금 및 거래내역 API")
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class TransactionController {

    private final WalletQueryService walletQueryService;

    /**
     * 월렛의 거래 내역 조회
     *
     * @param walletId  월렛 ID
     * @param startDate 시작일 (ISO 8601 + Offset)
     * @param endDate   종료일 (ISO 8601 + Offset)
     * @param pageable  페이징 정보
     * @return 페이징된 거래 내역
     */
    @Operation(summary = "거래 내역 조회", description = "특정 월렛의 거래 내역을 조회합니다. 날짜 범위 필터링과 페이징을 지원합니다.")
    @GetMapping("/{walletId}/transactions")
    public ApiResponse<TransactionListResponse> getTransactions(
            @PathVariable String walletId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @PageableDefault(size = 20, sort = "withdrawalDate", direction = Direction.DESC) Pageable pageable) {

        TransactionListResponse response = walletQueryService.getTransactions(walletId, startDate, endDate, pageable);

        return ApiResponse.success(response);
    }
}
