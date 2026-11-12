package com.islandride.core.exception;

public class BusinessRuleException extends RuntimeException {
    private final String errorType;

    public BusinessRuleException(String message) {
        super(message);
        this.errorType = "https://errors.islandride.in/business-rule-violation";
    }

    public BusinessRuleException(String errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public BusinessRuleException(String message, Throwable cause) {
        super(message, cause);
        this.errorType = "https://errors.islandride.in/business-rule-violation";
    }

    public String getErrorType() {
        return errorType;
    }
}
