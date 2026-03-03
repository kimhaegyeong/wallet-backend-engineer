package com.sentbe.wallet.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TransactionListResponse {
    private List<WithdrawalResponse> transactions;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private int size;
}
