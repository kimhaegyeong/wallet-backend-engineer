package com.sentbe.wallet.exception;

import lombok.Getter;

@Getter
public class DateRangeExceededException extends RuntimeException {
    private final int maxDays;

    public DateRangeExceededException(int maxDays) {
        super("Query range exceeds maximum allowed days: " + maxDays);
        this.maxDays = maxDays;
    }
}
