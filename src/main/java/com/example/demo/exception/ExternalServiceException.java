package com.example.demo.exception;

/**
 * Custom exception for external service integration failures
 * Used when external service calls fail (e.g., product service unavailable)
 */
public class ExternalServiceException extends RuntimeException {
    private String serviceName;
    private String endpoint;
    private int httpStatus;

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, String serviceName) {
        super(message);
        this.serviceName = serviceName;
    }

    public ExternalServiceException(String message, String serviceName, String endpoint, int httpStatus) {
        super(message);
        this.serviceName = serviceName;
        this.endpoint = endpoint;
        this.httpStatus = httpStatus;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
