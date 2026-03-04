package com.sentbe.wallet.exception;

import lombok.Getter;

@Getter
public class WalletNotFoundException extends RuntimeException {
    private final String walletId;

    public WalletNotFoundException(String walletId) {
        super("월렛을 찾을 수 없습니다. (ID: " + walletId + ")");
        this.walletId = walletId;
    }
}
