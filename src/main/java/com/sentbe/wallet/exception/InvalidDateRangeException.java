package com.sentbe.wallet.exception;

public class InvalidDateRangeException extends RuntimeException {
    public InvalidDateRangeException() {
        super("Start date must be before or equal to end date.");
    }
}
