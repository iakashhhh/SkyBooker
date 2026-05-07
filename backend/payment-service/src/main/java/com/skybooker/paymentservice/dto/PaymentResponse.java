package com.skybooker.paymentservice.dto;

import com.skybooker.paymentservice.entity.PaymentMode;
import com.skybooker.paymentservice.entity.PaymentStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class PaymentResponse {
    private String paymentId;
    private String bookingId;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private PaymentMode paymentMode;
    private String razorpayKeyId;
    private String gatewayOrderId;
    private String transactionId;
    private String gatewayResponse;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;
    private BigDecimal refundedAmount;
}
