package com.example.demo.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Standard error response structure for API responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String message;
    private String errorCode;
    private int httpStatus;
    private LocalDateTime timestamp;
    private String path;
    private String details;
    private Object additionalInfo;
}
