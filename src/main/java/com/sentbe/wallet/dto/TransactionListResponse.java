package com.sentbe.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "거래 내역 목록 조회 응답")
public class TransactionListResponse {
    @Schema(description = "거래 내역 리스트")
    private List<WithdrawalResponse> transactions;

    @Schema(description = "전체 거래 건수", example = "100")
    private long totalElements;

    @Schema(description = "전체 페이지 수", example = "5")
    private int totalPages;

    @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0")
    private int currentPage;

    @Schema(description = "페이지당 건수", example = "20")
    private int size;
}
