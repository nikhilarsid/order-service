package com.example.demo.exception;

/**
 * Custom exception for business logic violations
 * Used when business rules are violated (e.g., insufficient stock, invalid status transition)
 */
public class BusinessRuleViolationException extends RuntimeException {
    private String errorCode;
    private String details;

    public BusinessRuleViolationException(String message) {
        super(message);
        this.errorCode = "BUSINESS_RULE_VIOLATION";
    }

    public BusinessRuleViolationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessRuleViolationException(String message, String errorCode, String details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getDetails() {
        return details;
    }
}
