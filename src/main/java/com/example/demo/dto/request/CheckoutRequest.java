package com.example.demo.dto.request;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class CheckoutRequest {
    private String firstName;
    private String lastName;
    private String address;
    private String paymentStatus;
}
