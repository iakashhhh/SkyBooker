package com.skybooker.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProcessPaymentRequest {

    @NotBlank
    private String paymentId;

    private boolean success;

    private String transactionId;

    private String gatewayResponse;
}
