package com.sentbe.wallet.exception;

import lombok.Getter;

@Getter
public class InsufficientBalanceException extends RuntimeException {
    private final long currentBalance;
    private final long requestedAmount;

    public InsufficientBalanceException(long currentBalance, long requestedAmount) {
        super(String.format("잔액이 부족합니다. (현재 잔액: %d, 요청 금액: %d)", currentBalance, requestedAmount));
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }
}
