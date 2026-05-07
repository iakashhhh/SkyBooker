package com.skybooker.paymentservice.dto;

import com.skybooker.paymentservice.entity.PaymentMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class InitiatePaymentRequest {

    private String bookingId;

    private String bookingReference;

    private Long userId;

    @NotNull
    private BigDecimal amount;

    @NotBlank
    private String currency;

    private PaymentMode paymentMode;
}
