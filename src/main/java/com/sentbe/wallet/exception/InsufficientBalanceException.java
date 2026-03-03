package com.sentbe.wallet.exception;

import lombok.Getter;

@Getter
public class InsufficientBalanceException extends RuntimeException {
    private final long currentBalance;
    private final long requestedAmount;

    public InsufficientBalanceException(long currentBalance, long requestedAmount) {
        super(String.format("Insufficient balance. Current: %d, Requested: %d", currentBalance, requestedAmount));
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }
}
