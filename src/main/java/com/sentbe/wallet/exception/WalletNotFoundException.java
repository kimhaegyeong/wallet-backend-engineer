package com.sentbe.wallet.exception;

import lombok.Getter;

@Getter
public class WalletNotFoundException extends RuntimeException {
    private final String walletId;

    public WalletNotFoundException(String walletId) {
        super("Wallet not found: " + walletId);
        this.walletId = walletId;
    }
}
