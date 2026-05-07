package com.skybooker.bookingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Internal payment service request contract.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    private String bookingId;
    private String bookingReference;
    private Long userId;
    private BigDecimal amount;
    private String currency;
}
