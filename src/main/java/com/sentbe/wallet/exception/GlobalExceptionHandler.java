package com.sentbe.wallet.exception;

import com.sentbe.wallet.dto.ApiResponse;
import com.sentbe.wallet.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리기
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        /**
         * 월렛을 찾을 수 없는 경우 (404)
         */
        @ExceptionHandler(WalletNotFoundException.class)
        public ResponseEntity<ApiResponse<Void>> handleWalletNotFoundException(WalletNotFoundException e) {
                log.warn("Wallet not found: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error(ErrorResponse.of("WALLET_NOT_FOUND", e.getMessage())));
        }

        /**
         * 잔고가 부족한 경우 (422)
         */
        @ExceptionHandler(InsufficientBalanceException.class)
        public ResponseEntity<ApiResponse<Void>> handleInsufficientBalanceException(InsufficientBalanceException e) {
                log.warn("Insufficient balance: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                .body(ApiResponse.error(ErrorResponse.of("INSUFFICIENT_BALANCE", e.getMessage())));
        }

        /**
         * 잘못된 금액 요청인 경우 (400)
         */
        @ExceptionHandler(InvalidAmountException.class)
        public ResponseEntity<ApiResponse<Void>> handleInvalidAmountException(InvalidAmountException e) {
                log.warn("Invalid amount: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(ErrorResponse.of("INVALID_AMOUNT", e.getMessage())));
        }

        /**
         * 잘못된 날짜 범위인 경우 (400)
         */
        @ExceptionHandler(InvalidDateRangeException.class)
        public ResponseEntity<ApiResponse<Void>> handleInvalidDateRangeException(InvalidDateRangeException e) {
                log.warn("Invalid date range: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(ErrorResponse.of("INVALID_DATE_RANGE", e.getMessage())));
        }

        /**
         * 최대 조회 기간을 초과한 경우 (400)
         */
        @ExceptionHandler(DateRangeExceededException.class)
        public ResponseEntity<ApiResponse<Void>> handleDateRangeExceededException(DateRangeExceededException e) {
                log.warn("Date range exceeded: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(ErrorResponse.of("DATE_RANGE_EXCEEDED", e.getMessage())));
        }

        /**
         * 락 획득 타임아웃 발생 시 (503)
         */
        @ExceptionHandler(LockAcquisitionTimeoutException.class)
        public ResponseEntity<ApiResponse<Void>> handleLockAcquisitionTimeoutException(
                        LockAcquisitionTimeoutException e) {
                log.error("Lock acquisition timeout: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body(ApiResponse.error(ErrorResponse.of("LOCK_ACQUISITION_TIMEOUT", e.getMessage())));
        }

        /**
         * Bean Validation 실패 시 (400)
         */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
                        MethodArgumentNotValidException e) {
                log.warn("Validation failed: {}", e.getMessage());
                BindingResult bindingResult = e.getBindingResult();
                List<ErrorResponse.FieldError> fieldErrors = bindingResult.getFieldErrors().stream()
                                .map(error -> ErrorResponse.FieldError.builder()
                                                .field(error.getField())
                                                .value(error.getRejectedValue() == null ? ""
                                                                : error.getRejectedValue().toString())
                                                .reason(error.getDefaultMessage())
                                                .build())
                                .collect(Collectors.toList());

                // transactionId 형식 오류인지 체크하여 코드 분기
                String code = fieldErrors.stream()
                                .anyMatch(fe -> "transactionId".equals(fe.getField())) ? "INVALID_TRANSACTION_ID_FORMAT"
                                                : "INVALID_PARAMETER";

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .code(code)
                                .message("요청 파라미터가 유효하지 않습니다.")
                                .errors(fieldErrors)
                                .build();

                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(errorResponse));
        }

        /**
         * 데이터 무결성 예외 발생 시 (200) - DuplicateKey(멱등) 처리
         * 서비스 레이어에서 처리되지 않은 중복 요청 가능성에 대한 2차 방어
         */
        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(
                        DataIntegrityViolationException e) {
                log.warn("Data integrity violation (possible duplicate request): {}", e.getMessage());
                // 멱등 중복 요청으로 간주하여 200 반환
                return ResponseEntity.ok(ApiResponse.<Void>builder()
                                .success(true)
                                .idempotent(true)
                                .data(null)
                                .build());
        }

        /**
         * 기타 모든 예외 (500)
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleAllExceptions(Exception e) {
                log.error("Unexpected error occurred: ", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse
                                                .error(ErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.")));
        }
}
