package com.sentbe.wallet.exception;

public class LockAcquisitionTimeoutException extends RuntimeException {
    public LockAcquisitionTimeoutException() {
        super("Failed to acquire lock within timeout period.");
    }
}
