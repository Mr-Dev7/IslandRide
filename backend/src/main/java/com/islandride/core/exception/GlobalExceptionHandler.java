package com.islandride.core.exception;

import com.islandride.core.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

