package com.sentbe.wallet.exception;

import lombok.Getter;

@Getter
public class DateRangeExceededException extends RuntimeException {
    private final int maxDays;

    public DateRangeExceededException(int maxDays) {
        super("최대 조회 가능 기간(" + maxDays + "일)을 초과하였습니다.");
        this.maxDays = maxDays;
    }
}
