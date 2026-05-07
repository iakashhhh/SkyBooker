package com.skybooker.paymentservice.service;

import com.skybooker.paymentservice.dto.InitiatePaymentRequest;
import com.skybooker.paymentservice.dto.PaymentResponse;
import com.skybooker.paymentservice.dto.ProcessPaymentRequest;
import com.skybooker.paymentservice.dto.RefundPaymentRequest;
import com.skybooker.paymentservice.dto.VerifyPaymentRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PaymentService {

    String getRazorpayKeyId();

    PaymentResponse initiatePayment(InitiatePaymentRequest request);

    PaymentResponse processPayment(ProcessPaymentRequest request);

    PaymentResponse verifyPayment(VerifyPaymentRequest request);

    PaymentResponse refundPayment(RefundPaymentRequest request);

    PaymentResponse getPaymentByBooking(String bookingId);

    List<PaymentResponse> getAllPayments();

    List<PaymentResponse> getPaymentsByUser(Long userId);

    String getPaymentStatus(String paymentId);

    String generateReceipt(String paymentId);

    BigDecimal getRevenue(LocalDate fromDate, LocalDate toDate);
}
