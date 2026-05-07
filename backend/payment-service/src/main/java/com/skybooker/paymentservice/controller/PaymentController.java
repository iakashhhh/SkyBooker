package com.skybooker.paymentservice.controller;

import com.skybooker.paymentservice.dto.InitiatePaymentRequest;
import com.skybooker.paymentservice.dto.PaymentResponse;
import com.skybooker.paymentservice.dto.ProcessPaymentRequest;
import com.skybooker.paymentservice.dto.RefundPaymentRequest;
import com.skybooker.paymentservice.dto.VerifyPaymentRequest;
import com.skybooker.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/initiate")
    public PaymentResponse initiate(@Valid @RequestBody InitiatePaymentRequest request) {
        return paymentService.initiatePayment(request);
    }

    @GetMapping("/key")
    public Map<String, String> key() {
        return Map.of("key", paymentService.getRazorpayKeyId());
    }

    @PostMapping("/process")
    public PaymentResponse process(@Valid @RequestBody ProcessPaymentRequest request) {
        return paymentService.processPayment(request);
    }

    @PostMapping("/verify")
    public PaymentResponse verify(@Valid @RequestBody VerifyPaymentRequest request) {
        return paymentService.verifyPayment(request);
    }

    @PostMapping("/refund")
    public PaymentResponse refund(@Valid @RequestBody RefundPaymentRequest request) {
        return paymentService.refundPayment(request);
    }

    @GetMapping("/booking/{bookingId}")
    public PaymentResponse byBooking(@PathVariable String bookingId) {
        return paymentService.getPaymentByBooking(bookingId);
    }

    @GetMapping
    public List<PaymentResponse> allPayments() {
        return paymentService.getAllPayments();
    }

    @GetMapping("/user/{userId}")
    public List<PaymentResponse> byUser(@PathVariable Long userId) {
        return paymentService.getPaymentsByUser(userId);
    }

    @GetMapping("/status/{paymentId}")
    public Map<String, String> status(@PathVariable String paymentId) {
        return Map.of("paymentId", paymentId, "status", paymentService.getPaymentStatus(paymentId));
    }

    @GetMapping("/receipt/{paymentId}")
    public Map<String, String> receipt(@PathVariable String paymentId) {
        return Map.of("paymentId", paymentId, "receipt", paymentService.generateReceipt(paymentId));
    }

    @GetMapping("/revenue")
    public Map<String, BigDecimal> revenue(@RequestParam LocalDate fromDate,
                                           @RequestParam LocalDate toDate) {
        return Map.of("revenue", paymentService.getRevenue(fromDate, toDate));
    }
}
