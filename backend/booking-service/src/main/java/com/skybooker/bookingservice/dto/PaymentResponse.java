package com.skybooker.bookingservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal payment service response contract.
 */
@Data
@NoArgsConstructor
public class PaymentResponse {
    private String paymentId;
    private String status;
}
