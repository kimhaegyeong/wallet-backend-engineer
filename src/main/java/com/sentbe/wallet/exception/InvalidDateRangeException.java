package com.sentbe.wallet.exception;

public class InvalidDateRangeException extends RuntimeException {
    public InvalidDateRangeException() {
        super("시작일은 종료일보다 빨라야 합니다.");
    }
}
