package com.skybooker.paymentservice.controller;

import com.skybooker.paymentservice.dto.InitiatePaymentRequest;
import com.skybooker.paymentservice.dto.PaymentResponse;
import com.skybooker.paymentservice.dto.ProcessPaymentRequest;
import com.skybooker.paymentservice.dto.RefundPaymentRequest;
import com.skybooker.paymentservice.dto.VerifyPaymentRequest;
import com.skybooker.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentControllerTest {

    @Test
    void shouldDelegateAllPaymentEndpoints() {
        PaymentService service = mock(PaymentService.class);
        PaymentController controller = new PaymentController(service);

        PaymentResponse response = new PaymentResponse();
        response.setPaymentId("PAY-1");
        response.setStatus(com.skybooker.paymentservice.entity.PaymentStatus.PAID);

        when(service.initiatePayment(org.mockito.ArgumentMatchers.any(InitiatePaymentRequest.class))).thenReturn(response);
        when(service.processPayment(org.mockito.ArgumentMatchers.any(ProcessPaymentRequest.class))).thenReturn(response);
        when(service.verifyPayment(org.mockito.ArgumentMatchers.any(VerifyPaymentRequest.class))).thenReturn(response);
        when(service.refundPayment(org.mockito.ArgumentMatchers.any(RefundPaymentRequest.class))).thenReturn(response);
        when(service.getPaymentByBooking("BKG-1")).thenReturn(response);
        when(service.getAllPayments()).thenReturn(List.of(response));
        when(service.getPaymentsByUser(9L)).thenReturn(List.of(response));
        when(service.getPaymentStatus("PAY-1")).thenReturn("PAID");
        when(service.generateReceipt("PAY-1")).thenReturn("receipt");
        when(service.getRevenue(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))).thenReturn(new BigDecimal("5000"));
        when(service.getRazorpayKeyId()).thenReturn("rzp_key");

        assertEquals("PAY-1", controller.initiate(new InitiatePaymentRequest()).getPaymentId());
        assertEquals("rzp_key", controller.key().get("key"));
        assertEquals("PAY-1", controller.process(new ProcessPaymentRequest()).getPaymentId());
        assertEquals("PAY-1", controller.verify(new VerifyPaymentRequest()).getPaymentId());
        assertEquals("PAY-1", controller.refund(new RefundPaymentRequest()).getPaymentId());
        assertEquals("PAY-1", controller.byBooking("BKG-1").getPaymentId());
        assertEquals(1, controller.allPayments().size());
        assertEquals(1, controller.byUser(9L).size());
        assertEquals("PAID", controller.status("PAY-1").get("status"));
        assertEquals("receipt", controller.receipt("PAY-1").get("receipt"));
        assertEquals(new BigDecimal("5000"), controller.revenue(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)).get("revenue"));

        verify(service).getPaymentByBooking("BKG-1");
    }
}
