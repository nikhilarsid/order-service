package com.example.demo.exception;

/**
 * Custom exception for validation errors
 * Used when input validation fails (e.g., null fields, invalid format)
 */
public class ValidationException extends RuntimeException {
    private String fieldName;
    private String fieldValue;
    private String reason;

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String fieldName, String reason) {
        super(String.format("Validation failed for field '%s': %s", fieldName, reason));
        this.fieldName = fieldName;
        this.reason = reason;
    }

    public ValidationException(String fieldName, String fieldValue, String reason) {
        super(String.format("Validation failed for field '%s' with value '%s': %s", fieldName, fieldValue, reason));
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
        this.reason = reason;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public String getReason() {
        return reason;
    }
}
