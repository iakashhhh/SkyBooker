package com.skybooker.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyPaymentRequest {

    @NotBlank
    private String bookingId;

    private String paymentId;

    private String razorpayOrderId;

    private String razorpayPaymentId;

    private String razorpaySignature;

    private String gatewayResponse;
}
