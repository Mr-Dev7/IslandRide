package com.islandride.core.exception;

import com.islandride.core.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        log.warn("Resource not found [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
                "https://errors.islandride.in/resource-not-found",
                "Resource Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        log.warn("Business rule violation [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
                ex.getErrorType(),
                "Business Rule Violation",
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);

        List<ErrorResponse.ValidationError> invalidParams = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(err -> {
                    String field = err instanceof FieldError fe ? fe.getField() : err.getObjectName();
                    return new ErrorResponse.ValidationError(field, err.getDefaultMessage());
                })
                .toList();

        ErrorResponse error = ErrorResponse.withValidation(
                "https://errors.islandride.in/validation-error",
                "Request Validation Failed",
                HttpStatus.BAD_REQUEST.value(),
                "One or more fields in the request body failed validation constraints.",
                request.getRequestURI(),
                correlationId,
                invalidParams
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler({
        org.springframework.security.authentication.BadCredentialsException.class,
        SecurityException.class
    })
    public ResponseEntity<ErrorResponse> handleAuthenticationException(Exception ex, HttpServletRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        log.warn("Authentication failure [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
                "https://errors.islandride.in/unauthorized",
                "Authentication Failed",
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler({
        org.springframework.security.authentication.LockedException.class,
        org.springframework.security.authentication.DisabledException.class
    })
    public ResponseEntity<ErrorResponse> handleAccountStatusException(Exception ex, HttpServletRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        log.warn("Account status restriction [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
                "https://errors.islandride.in/account-restricted",
                "Account Inactive or Locked",
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        log.warn("Access denied [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
                "https://errors.islandride.in/forbidden",
                "Access Forbidden",
                HttpStatus.FORBIDDEN.value(),
                "You do not possess the required permissions to access this resource.",
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        log.warn("Resource conflict [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
                "https://errors.islandride.in/conflict",
                "Conflict",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        log.warn("Concurrent update conflict [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
                "https://errors.islandride.in/concurrent-modification",
                "Concurrent Modification Conflict",
                HttpStatus.CONFLICT.value(),
                "The record was modified concurrently. Please refresh and try again.",
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        log.warn("Rate limit exceeded [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
                "https://errors.islandride.in/rate-limit-exceeded",
                "Too Many Requests",
                HttpStatus.TOO_MANY_REQUESTS.value(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        log.warn("Upload size limit exceeded [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
                "https://errors.islandride.in/payload-too-large",
                "File Too Large",
                HttpStatus.CONTENT_TOO_LARGE.value(),
                "Uploaded file exceeds maximum permitted size.",
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(error);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        ErrorResponse error = ErrorResponse.of(
                "https://errors.islandride.in/bad-request",
                "Missing Upload Part",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            org.springframework.web.servlet.resource.NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        ErrorResponse error = ErrorResponse.of(
                "https://errors.islandride.in/not-found",
                "Endpoint Not Found",
                HttpStatus.NOT_FOUND.value(),
                "The requested API endpoint does not exist.",
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        log.error("Unhandled internal server exception [correlationId={}]", correlationId, ex);

        ErrorResponse error = ErrorResponse.of(
                "https://errors.islandride.in/internal-server-error",
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected internal error occurred. Please reference the correlation ID when reporting.",
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

