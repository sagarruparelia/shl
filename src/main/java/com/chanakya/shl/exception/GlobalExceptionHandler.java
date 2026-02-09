package com.chanakya.shl.exception;

import com.chanakya.shl.model.dto.response.PasscodeErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.MissingRequestValueException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ShlNotFoundException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleNotFound(ShlNotFoundException ex) {
        log.debug("SHL not found: {}", ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage())));
    }

    @ExceptionHandler(ShlExpiredException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleExpired(ShlExpiredException ex) {
        log.debug("SHL expired: {}", ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "SHL not found")));
    }

    @ExceptionHandler(ShlInactiveException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleInactive(ShlInactiveException ex) {
        log.debug("SHL inactive: {}", ex.getMessage());
        return Mono.just(ResponseEntity.ok()
                .body(Map.of("status", "no-longer-valid", "files", List.of())));
    }

    @ExceptionHandler(InvalidPasscodeException.class)
    public Mono<ResponseEntity<PasscodeErrorResponse>> handleInvalidPasscode(InvalidPasscodeException ex) {
        log.debug("Invalid passcode: {}", ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(PasscodeErrorResponse.builder()
                        .error("Invalid passcode")
                        .remainingAttempts(ex.getRemainingAttempts())
                        .build()));
    }

    @ExceptionHandler(MissingRequestValueException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleMissingParam(MissingRequestValueException ex) {
        log.debug("Missing request value: {}", ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getReason())));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleValidation(WebExchangeBindException ex) {
        String message = ex.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        log.debug("Validation error: {}", message);
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", message)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleBadRequest(IllegalArgumentException ex) {
        log.debug("Bad request: {}", ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, String>>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error")));
    }
}
