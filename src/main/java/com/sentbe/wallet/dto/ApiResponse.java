package com.sentbe.wallet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전역 공통 응답 포맷
 * 
 * @param <T> 응답 데이터 타입
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "전역 공통 응답 포맷")
public class ApiResponse<T> {

    @Schema(description = "요청 성공 여부", example = "true")
    private boolean success;

    @Builder.Default
    @Schema(description = "멱등성 보장 응답 여부 (중복 요청 시 true)", example = "false")
    private boolean idempotent = false;

    @Schema(description = "응답 데이터")
    private T data;

    @Schema(description = "에러 정보 (실패 시에만 포함)")
    private ErrorResponse error;

    /**
     * 성공 응답 생성 (데이터 포함)
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    /**
     * 성공 응답 생성 (데이터 + 멱등성 플래그 포함)
     */
    public static <T> ApiResponse<T> success(T data, boolean idempotent) {
        return ApiResponse.<T>builder()
                .success(true)
                .idempotent(idempotent)
                .data(data)
                .build();
    }

    /**
     * 실패 응답 생성
     */
    public static <T> ApiResponse<T> error(ErrorResponse error) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(error)
                .build();
    }
}
