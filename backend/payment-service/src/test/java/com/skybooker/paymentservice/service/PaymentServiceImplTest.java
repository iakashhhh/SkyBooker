package com.skybooker.paymentservice.service;

import com.skybooker.paymentservice.dto.InitiatePaymentRequest;
import com.skybooker.paymentservice.dto.ProcessPaymentRequest;
import com.skybooker.paymentservice.dto.RefundPaymentRequest;
import com.skybooker.paymentservice.dto.VerifyPaymentRequest;
import com.skybooker.paymentservice.entity.PaymentMode;
import com.skybooker.paymentservice.entity.Payment;
import com.skybooker.paymentservice.entity.PaymentStatus;
import com.skybooker.paymentservice.exception.BadRequestException;
import com.skybooker.paymentservice.exception.ResourceNotFoundException;
import com.skybooker.paymentservice.integration.BookingServiceClient;
import com.skybooker.paymentservice.repository.PaymentRepository;
import com.skybooker.paymentservice.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BookingServiceClient bookingServiceClient;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentRepository, bookingServiceClient);
    }

    @Test
    void shouldInitiatePendingPayment() {
        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setBookingId("1001");
        request.setUserId(10L);
        request.setAmount(new BigDecimal("1999.00"));
        request.setCurrency("INR");
        request.setPaymentMode(PaymentMode.UPI);

        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(PaymentStatus.PENDING, paymentService.initiatePayment(request).getStatus());
    }

    @Test
    void shouldMarkPaymentPaid() {
        com.skybooker.paymentservice.entity.Payment payment = new com.skybooker.paymentservice.entity.Payment();
        payment.setPaymentId("pay-1");
        payment.setBookingId("101");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("200.00"));
        payment.setCurrency("INR");
        payment.setUserId(1L);
        payment.setPaymentMode(PaymentMode.CARD);

        when(paymentRepository.findById("pay-1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setPaymentId("pay-1");
        request.setSuccess(true);
        request.setGatewayResponse("ok");

        paymentService.processPayment(request);

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(bookingServiceClient).updateBookingStatus(org.mockito.ArgumentMatchers.eq("101"), statusCaptor.capture());
        assertEquals("CONFIRMED", statusCaptor.getValue());
    }

    @Test
    void shouldKeepPaymentPaidWhenBookingConfirmationFails() {
        com.skybooker.paymentservice.entity.Payment payment = new com.skybooker.paymentservice.entity.Payment();
        payment.setPaymentId("pay-2");
        payment.setBookingId("202");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("300.00"));
        payment.setCurrency("INR");
        payment.setUserId(2L);
        payment.setPaymentMode(PaymentMode.UPI);

        when(paymentRepository.findById("pay-2")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("Seat hold expired")).when(bookingServiceClient).updateBookingStatus("202", "CONFIRMED");

        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setPaymentId("pay-2");
        request.setSuccess(true);
        request.setGatewayResponse("approved");

        var response = paymentService.processPayment(request);
        assertEquals(PaymentStatus.PAID, response.getStatus());
        assertTrue(String.valueOf(response.getGatewayResponse()).contains("Booking confirmation failed"));
    }

    @Test
    void shouldRejectInitiatePaymentWhenBookingIdMissing() {
        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setAmount(new BigDecimal("1200.00"));
        request.setCurrency("INR");
        request.setPaymentMode(PaymentMode.UPI);

        assertThrows(BadRequestException.class, () -> paymentService.initiatePayment(request));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void shouldMarkPaymentFailedAndCancelBookingWhenGatewayFails() {
        Payment payment = new Payment();
        payment.setPaymentId("pay-failed");
        payment.setBookingId("303");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("450.00"));
        payment.setCurrency("INR");
        payment.setUserId(9L);
        payment.setPaymentMode(PaymentMode.NET_BANKING);

        when(paymentRepository.findById("pay-failed")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setPaymentId("pay-failed");
        request.setSuccess(false);
        request.setGatewayResponse("declined");

        var response = paymentService.processPayment(request);
        assertEquals(PaymentStatus.FAILED, response.getStatus());
        verify(bookingServiceClient).updateBookingStatus("303", "CANCELLED");
    }

    @Test
    void shouldRejectRefundWhenPaymentIsNotPaid() {
        Payment payment = new Payment();
        payment.setPaymentId("pay-pending");
        payment.setBookingId("500");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("800.00"));
        payment.setCurrency("INR");
        payment.setUserId(2L);
        payment.setPaymentMode(PaymentMode.CARD);

        when(paymentRepository.findById("pay-pending")).thenReturn(Optional.of(payment));

        RefundPaymentRequest request = new RefundPaymentRequest();
        request.setPaymentId("pay-pending");
        request.setAmount(new BigDecimal("200.00"));

        assertThrows(BadRequestException.class, () -> paymentService.refundPayment(request));
    }

    @Test
    void shouldRejectVerifyWhenPaymentIdDoesNotMatchBooking() {
        Payment payment = new Payment();
        payment.setPaymentId("pay-actual");
        payment.setBookingId("BKG-99");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("1000.00"));
        payment.setCurrency("INR");
        payment.setUserId(88L);
        payment.setPaymentMode(PaymentMode.UPI);

        when(paymentRepository.findByBookingId("BKG-99")).thenReturn(Optional.of(payment));

        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setBookingId("BKG-99");
        request.setPaymentId("pay-other");
        request.setRazorpayPaymentId("rzp_1");

        assertThrows(BadRequestException.class, () -> paymentService.verifyPayment(request));
    }

    @Test
    void shouldUseBookingReferenceWhenBookingIdMissing() {
        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setBookingReference("BKG-REF-1");
        request.setUserId(10L);
        request.setAmount(new BigDecimal("2500.00"));
        request.setCurrency("inr");
        request.setPaymentMode(PaymentMode.CARD);

        when(paymentRepository.findByBookingId("BKG-REF-1")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = paymentService.initiatePayment(request);
        assertEquals("BKG-REF-1", response.getBookingId());
        assertEquals(PaymentStatus.PENDING, response.getStatus());
    }

    @Test
    void shouldMarkVerifyAsFailedWhenSignatureDataMissing() {
        Payment payment = new Payment();
        payment.setPaymentId("pay-v1");
        payment.setBookingId("BKG-V1");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("1200.00"));
        payment.setCurrency("INR");
        payment.setUserId(12L);
        payment.setPaymentMode(PaymentMode.UPI);

        when(paymentRepository.findByBookingId("BKG-V1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setBookingId("BKG-V1");
        request.setRazorpayPaymentId("");
        request.setRazorpaySignature("");

        var response = paymentService.verifyPayment(request);
        assertEquals(PaymentStatus.FAILED, response.getStatus());
        verify(bookingServiceClient).updateBookingStatus("BKG-V1", "CANCELLED");
    }

    @Test
    void shouldReturnPaidPaymentDirectlyOnVerify() {
        Payment payment = new Payment();
        payment.setPaymentId("pay-paid");
        payment.setBookingId("BKG-PAID");
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(new BigDecimal("1200.00"));
        payment.setCurrency("INR");
        payment.setUserId(12L);
        payment.setPaymentMode(PaymentMode.UPI);

        when(paymentRepository.findByBookingId("BKG-PAID")).thenReturn(Optional.of(payment));

        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setBookingId("BKG-PAID");

        var response = paymentService.verifyPayment(request);
        assertEquals(PaymentStatus.PAID, response.getStatus());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void shouldRefundPaidPayment() {
        Payment payment = new Payment();
        payment.setPaymentId("pay-refund");
        payment.setBookingId("BKG-REFUND");
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(new BigDecimal("900.00"));
        payment.setCurrency("INR");
        payment.setUserId(12L);
        payment.setPaymentMode(PaymentMode.UPI);

        when(paymentRepository.findById("pay-refund")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefundPaymentRequest request = new RefundPaymentRequest();
        request.setPaymentId("pay-refund");
        request.setAmount(new BigDecimal("300.00"));

        var response = paymentService.refundPayment(request);
        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
        assertEquals(new BigDecimal("300.00"), response.getRefundedAmount());
        verify(bookingServiceClient).updateBookingStatus("BKG-REFUND", "CANCELLED");
    }

    @Test
    void shouldExposeStatusReceiptAndRevenue() {
        Payment payment = new Payment();
        payment.setPaymentId("pay-report");
        payment.setBookingId("BKG-REPORT");
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(new BigDecimal("1000.00"));
        payment.setCurrency("INR");
        payment.setUserId(7L);
        payment.setPaymentMode(PaymentMode.WALLET);
        payment.setTransactionId("TXN-777");

        when(paymentRepository.findById("pay-report")).thenReturn(Optional.of(payment));
        when(paymentRepository.findByPaidAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(payment));
        when(paymentRepository.findAll()).thenReturn(List.of(payment));
        when(paymentRepository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(payment));
        when(paymentRepository.findByBookingId("BKG-REPORT")).thenReturn(Optional.of(payment));

        assertEquals("PAID", paymentService.getPaymentStatus("pay-report"));
        assertTrue(paymentService.generateReceipt("pay-report").contains("Payment ID: pay-report"));
        assertEquals(new BigDecimal("1000.00"), paymentService.getRevenue(LocalDate.now().minusDays(1), LocalDate.now()));
        assertEquals(1, paymentService.getAllPayments().size());
        assertEquals(1, paymentService.getPaymentsByUser(7L).size());
        assertEquals("BKG-REPORT", paymentService.getPaymentByBooking("BKG-REPORT").getBookingId());
    }

    @Test
    void shouldRejectInitiateWhenExistingPaymentWasRefunded() {
        Payment existing = new Payment();
        existing.setPaymentId("pay-old");
        existing.setBookingId("BKG-OLD");
        existing.setStatus(PaymentStatus.REFUNDED);
        existing.setAmount(new BigDecimal("900.00"));
        existing.setCurrency("INR");
        existing.setUserId(7L);
        existing.setPaymentMode(PaymentMode.UPI);

        when(paymentRepository.findByBookingId("BKG-OLD")).thenReturn(Optional.of(existing));

        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setBookingId("BKG-OLD");
        request.setAmount(new BigDecimal("1000.00"));
        request.setCurrency("INR");
        request.setPaymentMode(PaymentMode.CARD);

        assertThrows(BadRequestException.class, () -> paymentService.initiatePayment(request));
    }

    @Test
    void shouldRejectRefundWhenRequestedAmountExceedsPaidAmount() {
        Payment payment = new Payment();
        payment.setPaymentId("pay-limit");
        payment.setBookingId("BKG-LIMIT");
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(new BigDecimal("500.00"));
        payment.setCurrency("INR");
        payment.setUserId(15L);
        payment.setPaymentMode(PaymentMode.UPI);

        when(paymentRepository.findById("pay-limit")).thenReturn(Optional.of(payment));

        RefundPaymentRequest request = new RefundPaymentRequest();
        request.setPaymentId("pay-limit");
        request.setAmount(new BigDecimal("700.00"));

        assertThrows(BadRequestException.class, () -> paymentService.refundPayment(request));
    }

    @Test
    void shouldRejectProcessWhenPaymentAlreadyPaid() {
        Payment payment = new Payment();
        payment.setPaymentId("pay-paid");
        payment.setBookingId("BKG-PAID-2");
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(new BigDecimal("900.00"));
        payment.setCurrency("INR");
        payment.setUserId(7L);
        payment.setPaymentMode(PaymentMode.UPI);

        when(paymentRepository.findById("pay-paid")).thenReturn(Optional.of(payment));

        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setPaymentId("pay-paid");
        request.setSuccess(true);
        request.setGatewayResponse("already done");

        assertThrows(BadRequestException.class, () -> paymentService.processPayment(request));
    }

    @Test
    void shouldThrowWhenPaymentByBookingDoesNotExist() {
        when(paymentRepository.findByBookingId("BKG-NONE")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> paymentService.getPaymentByBooking("BKG-NONE"));
    }

    @Test
    void shouldRejectVerifyWhenPaymentAlreadyRefunded() {
        Payment payment = new Payment();
        payment.setPaymentId("pay-ref");
        payment.setBookingId("BKG-REF");
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setAmount(new BigDecimal("1200.00"));
        payment.setCurrency("INR");
        payment.setUserId(2L);
        payment.setPaymentMode(PaymentMode.UPI);

        when(paymentRepository.findByBookingId("BKG-REF")).thenReturn(Optional.of(payment));

        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setBookingId("BKG-REF");
        request.setPaymentId("pay-ref");

        assertThrows(BadRequestException.class, () -> paymentService.verifyPayment(request));
    }

    @Test
    void shouldRejectProcessWhenPaymentAlreadyRefunded() {
        Payment payment = new Payment();
        payment.setPaymentId("pay-refunded");
        payment.setBookingId("BKG-R");
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setAmount(new BigDecimal("1200.00"));
        payment.setCurrency("INR");
        payment.setUserId(2L);
        payment.setPaymentMode(PaymentMode.UPI);

        when(paymentRepository.findById("pay-refunded")).thenReturn(Optional.of(payment));

        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setPaymentId("pay-refunded");
        request.setSuccess(true);
        request.setGatewayResponse("ok");

        assertThrows(BadRequestException.class, () -> paymentService.processPayment(request));
    }

    @Test
    void shouldRejectInitiateWhenAmountIsZero() {
        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setBookingId("BKG-ZERO");
        request.setUserId(10L);
        request.setAmount(BigDecimal.ZERO);
        request.setCurrency("INR");
        request.setPaymentMode(PaymentMode.CARD);

        when(paymentRepository.findByBookingId("BKG-ZERO")).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> paymentService.initiatePayment(request));
    }

    @Test
    void shouldThrowWhenProcessingUnknownPayment() {
        when(paymentRepository.findById("missing-pay")).thenReturn(Optional.empty());

        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setPaymentId("missing-pay");
        request.setSuccess(true);
        request.setGatewayResponse("ok");

        assertThrows(ResourceNotFoundException.class, () -> paymentService.processPayment(request));
    }

    @Test
    void shouldKeepFailedStatusEvenWhenBookingCancellationThrows() {
        Payment payment = new Payment();
        payment.setPaymentId("pay-cancel-fail");
        payment.setBookingId("BKG-CANCEL-FAIL");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("600.00"));
        payment.setCurrency("INR");
        payment.setUserId(11L);
        payment.setPaymentMode(PaymentMode.UPI);

        when(paymentRepository.findById("pay-cancel-fail")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("booking service unavailable"))
            .when(bookingServiceClient).updateBookingStatus("BKG-CANCEL-FAIL", "CANCELLED");

        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setPaymentId("pay-cancel-fail");
        request.setSuccess(false);
        request.setGatewayResponse("declined");

        var response = paymentService.processPayment(request);

        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertEquals("declined", response.getGatewayResponse());
    }

    @Test
    void shouldReuseExistingPaidPaymentWithoutSaving() {
        Payment existing = new Payment();
        existing.setPaymentId("pay-existing");
        existing.setBookingId("BKG-EXISTING");
        existing.setStatus(PaymentStatus.PAID);
        existing.setAmount(new BigDecimal("950.00"));
        existing.setCurrency("INR");
        existing.setUserId(17L);
        existing.setPaymentMode(PaymentMode.CARD);

        when(paymentRepository.findByBookingId("BKG-EXISTING")).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setBookingId("BKG-EXISTING");
        request.setAmount(new BigDecimal("1000.00"));
        request.setCurrency("USD");
        request.setPaymentMode(PaymentMode.UPI);

        var response = paymentService.initiatePayment(request);

        assertEquals("pay-existing", response.getPaymentId());
        assertEquals(PaymentStatus.PAID, response.getStatus());
        verify(paymentRepository).save(existing);
    }

    @Test
    void shouldRegeneratePendingExistingPaymentWithNormalizedDefaults() {
        Payment existing = new Payment();
        existing.setPaymentId("pay-existing-pending");
        existing.setBookingId("BKG-PENDING-EXISTING");
        existing.setStatus(PaymentStatus.FAILED);
        existing.setAmount(new BigDecimal("900.00"));
        existing.setCurrency("EUR");
        existing.setUserId(17L);
        existing.setPaymentMode(PaymentMode.CARD);
        existing.setTransactionId("TXN-OLD");
        existing.setGatewayResponse("old");
        existing.setRefundedAmount(new BigDecimal("10.00"));
        existing.setRefundedAt(LocalDateTime.now().minusDays(1));
        existing.setPaidAt(LocalDateTime.now().minusDays(2));

        when(paymentRepository.findByBookingId("BKG-PENDING-EXISTING")).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setBookingId("BKG-PENDING-EXISTING");
        request.setAmount(null);
        request.setCurrency("  ");
        request.setUserId(null);
        request.setPaymentMode(null);

        var response = paymentService.initiatePayment(request);

        assertEquals(PaymentStatus.PENDING, response.getStatus());
        assertEquals("INR", response.getCurrency());
        assertEquals(PaymentMode.CARD, response.getPaymentMode());
        assertEquals(existing.getUserId(), response.getUserId());
        assertEquals(existing.getAmount(), response.getAmount());
        assertEquals(null, response.getTransactionId());
        assertEquals(null, response.getGatewayResponse());
    }

    @Test
    void shouldVerifyPaymentWithConfiguredRazorpaySignature() throws Exception {
        Payment signedPayment = new Payment();
        signedPayment.setPaymentId("pay-signature");
        signedPayment.setBookingId("BKG-SIGN");
        signedPayment.setStatus(PaymentStatus.PENDING);
        signedPayment.setAmount(new BigDecimal("1250.00"));
        signedPayment.setCurrency("INR");
        signedPayment.setUserId(44L);
        signedPayment.setPaymentMode(PaymentMode.UPI);
        signedPayment.setGatewayOrderId("order_123");

        PaymentServiceImpl signedService = new PaymentServiceImpl(
            paymentRepository,
            bookingServiceClient,
            null,
            "skybooker.events",
            "key_test",
            "secret_test",
            "https://api.razorpay.com"
        );

        when(paymentRepository.findByBookingId("BKG-SIGN")).thenReturn(Optional.of(signedPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setBookingId("BKG-SIGN");
        request.setPaymentId("pay-signature");
        request.setRazorpayOrderId("order_123");
        request.setRazorpayPaymentId("pay_rzp_123");

        String payload = "order_123|pay_rzp_123";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secret_test".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        request.setRazorpaySignature(HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))));

        var response = signedService.verifyPayment(request);

        assertEquals(PaymentStatus.PAID, response.getStatus());
        assertEquals("pay_rzp_123", response.getTransactionId());
        verify(bookingServiceClient).updateBookingStatus("BKG-SIGN", "CONFIRMED");
    }

    @Test
    void shouldFailVerificationWhenConfiguredSecretAndOrderMissing() {
        Payment signedPayment = new Payment();
        signedPayment.setPaymentId("pay-signature-fail");
        signedPayment.setBookingId("BKG-SIGN-FAIL");
        signedPayment.setStatus(PaymentStatus.PENDING);
        signedPayment.setAmount(new BigDecimal("1250.00"));
        signedPayment.setCurrency("INR");
        signedPayment.setUserId(45L);
        signedPayment.setPaymentMode(PaymentMode.UPI);
        signedPayment.setGatewayOrderId("order_saved");

        PaymentServiceImpl signedService = new PaymentServiceImpl(
            paymentRepository,
            bookingServiceClient,
            null,
            "skybooker.events",
            "key_test",
            "secret_test",
            "https://api.razorpay.com"
        );

        when(paymentRepository.findByBookingId("BKG-SIGN-FAIL")).thenReturn(Optional.of(signedPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setBookingId("BKG-SIGN-FAIL");
        request.setPaymentId("pay-signature-fail");
        request.setRazorpayOrderId("   ");
        request.setRazorpayPaymentId("pay_rzp_999");
        request.setRazorpaySignature("abc");

        var response = signedService.verifyPayment(request);

        assertEquals(PaymentStatus.FAILED, response.getStatus());
        verify(bookingServiceClient).updateBookingStatus("BKG-SIGN-FAIL", "CANCELLED");
    }

    @Test
    void shouldFailGatewayInitializationWhenCredentialsConfiguredButApiUnavailable() {
        PaymentServiceImpl gatewayService = new PaymentServiceImpl(
            paymentRepository,
            bookingServiceClient,
            null,
            "skybooker.events",
            "key_test",
            "secret_test",
            "http://127.0.0.1:1"
        );

        when(paymentRepository.findByBookingId("BKG-GATEWAY")).thenReturn(Optional.empty());

        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setBookingId("BKG-GATEWAY");
        request.setUserId(77L);
        request.setAmount(new BigDecimal("3000.00"));
        request.setCurrency("INR");
        request.setPaymentMode(PaymentMode.CARD);

        assertThrows(BadRequestException.class, () -> gatewayService.initiatePayment(request));
    }

    @Test
    void shouldThrowWhenVerifyBookingReferenceDoesNotExist() {
        when(paymentRepository.findByBookingId("MISSING-VERIFY")).thenReturn(Optional.empty());

        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setBookingId("MISSING-VERIFY");
        request.setPaymentId("pay-404");
        request.setRazorpayPaymentId("rzp");

        assertThrows(ResourceNotFoundException.class, () -> paymentService.verifyPayment(request));
    }

    @Test
    void shouldKeepRevenueZeroWhenNoPaidPaymentsPresent() {
        Payment failed = new Payment();
        failed.setPaymentId("pay-failed-revenue");
        failed.setStatus(PaymentStatus.FAILED);
        failed.setAmount(new BigDecimal("200.00"));

        when(paymentRepository.findByPaidAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(failed));

        assertEquals(BigDecimal.ZERO, paymentService.getRevenue(LocalDate.now().minusDays(1), LocalDate.now()));
    }
}
