package com.example.demo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST) // This changes 500 to 400
public class PurchaseLimitException extends RuntimeException {
    public PurchaseLimitException(String message) {
        super(message);
    }
}
