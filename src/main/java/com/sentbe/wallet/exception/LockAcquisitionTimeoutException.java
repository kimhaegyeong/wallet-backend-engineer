package com.sentbe.wallet.exception;

public class LockAcquisitionTimeoutException extends RuntimeException {
    public LockAcquisitionTimeoutException() {
        super("락 획득에 실패했습니다. 잠시 후 다시 시도해주세요.");
    }
}
