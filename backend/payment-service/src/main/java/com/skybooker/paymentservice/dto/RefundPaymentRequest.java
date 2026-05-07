package com.skybooker.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class RefundPaymentRequest {

    @NotBlank
    private String paymentId;

    @NotNull
    private BigDecimal amount;
}
