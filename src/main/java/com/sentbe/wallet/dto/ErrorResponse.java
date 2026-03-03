package com.sentbe.wallet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 전역 공통 에러 응답 포맷
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "전역 공통 에러 응답 포맷")
public class ErrorResponse {

    @Schema(description = "에러 코드", example = "INSUFFICIENT_BALANCE")
    private String code;

    @Schema(description = "에러 메시지", example = "잔액이 부족합니다.")
    private String message;

    @Schema(description = "필드별 에러 목록 (유효성 검사 실패 시)")
    private List<FieldError> errors;

    public static ErrorResponse of(String code, String message) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .build();
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Schema(description = "필드별 에러 상세 정보")
    public static class FieldError {
        @Schema(description = "에러가 발생한 필드명", example = "amount")
        private String field;

        @Schema(description = "요청된 값", example = "-100")
        private String value;

        @Schema(description = "에러 원인", example = "출금액은 1원 이상이어야 합니다.")
        private String reason;
    }
}
