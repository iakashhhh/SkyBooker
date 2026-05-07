package com.skybooker.paymentservice.service.impl;

import com.skybooker.paymentservice.dto.InitiatePaymentRequest;
import com.skybooker.paymentservice.dto.PaymentResponse;
import com.skybooker.paymentservice.dto.ProcessPaymentRequest;
import com.skybooker.paymentservice.dto.RefundPaymentRequest;
import com.skybooker.paymentservice.dto.VerifyPaymentRequest;
import com.skybooker.paymentservice.entity.Payment;
import com.skybooker.paymentservice.entity.PaymentStatus;
import com.skybooker.paymentservice.exception.BadRequestException;
import com.skybooker.paymentservice.exception.ResourceNotFoundException;
import com.skybooker.paymentservice.integration.BookingServiceClient;
import com.skybooker.paymentservice.integration.dto.BookingSnapshot;
import com.skybooker.paymentservice.repository.PaymentRepository;
import com.skybooker.paymentservice.service.PaymentService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final int BOOKING_CONFIRMATION_MAX_ATTEMPTS = 3;
    private static final long BOOKING_CONFIRMATION_RETRY_DELAY_MS = 250L;

    private final PaymentRepository paymentRepository;
    private final BookingServiceClient bookingServiceClient;
    private final RabbitTemplate rabbitTemplate;
    private final String bookingEventExchange;
    private final String razorpayKeyId;
    private final String razorpayKeySecret;
    private final String razorpayApiBaseUrl;
    private final RestTemplate razorpayRestTemplate = new RestTemplate();

    @Autowired
    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              BookingServiceClient bookingServiceClient,
                              RabbitTemplate rabbitTemplate,
                              @Value("${booking.event-exchange:skybooker.events}") String bookingEventExchange,
                              @Value("${payment.razorpay.key-id:}") String razorpayKeyId,
                              @Value("${payment.razorpay.key-secret:}") String razorpayKeySecret,
                              @Value("${payment.razorpay.api-base-url:https://api.razorpay.com}") String razorpayApiBaseUrl) {
        this.paymentRepository = paymentRepository;
        this.bookingServiceClient = bookingServiceClient;
        this.rabbitTemplate = rabbitTemplate;
        this.bookingEventExchange = bookingEventExchange == null ? "skybooker.events" : bookingEventExchange;
        this.razorpayKeyId = razorpayKeyId == null ? "" : razorpayKeyId;
        this.razorpayKeySecret = razorpayKeySecret == null ? "" : razorpayKeySecret;
        this.razorpayApiBaseUrl = razorpayApiBaseUrl == null ? "https://api.razorpay.com" : razorpayApiBaseUrl.trim();
    }

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              BookingServiceClient bookingServiceClient) {
        this(paymentRepository, bookingServiceClient, null, "skybooker.events", "", "", "https://api.razorpay.com");
    }

    @Override
    @Transactional(readOnly = true)
    public String getRazorpayKeyId() {
        return razorpayKeyId.trim();
    }

    @Override
    @Transactional
    public PaymentResponse initiatePayment(InitiatePaymentRequest request) {
        String bookingId = request.getBookingId() != null && !request.getBookingId().isBlank()
            ? request.getBookingId()
            : request.getBookingReference();
        if (bookingId == null || bookingId.isBlank()) {
            throw new BadRequestException("bookingId or bookingReference is required");
        }

        Payment payment = paymentRepository.findByBookingId(bookingId)
            .map(existing -> prepareExistingPayment(existing, request))
            .orElseGet(() -> newPayment(request, bookingId));

        payment.setGatewayOrderId(createGatewayOrderId(payment));
        return toResponse(paymentRepository.save(payment));
    }

    @Override
    @Transactional
    public PaymentResponse verifyPayment(VerifyPaymentRequest request) {
        Payment payment = findPaymentForVerification(request);

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new BadRequestException("Refunded payment cannot be verified again");
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            return toResponse(payment);
        }

        String orderId = request.getRazorpayOrderId() == null || request.getRazorpayOrderId().isBlank()
            ? payment.getGatewayOrderId()
            : request.getRazorpayOrderId();
        boolean verified = verifySignatureIfConfigured(orderId, request.getRazorpayPaymentId(), request.getRazorpaySignature());

        if (verified) {
            payment.setPaidAt(LocalDateTime.now());
            payment.setTransactionId(request.getRazorpayPaymentId() == null || request.getRazorpayPaymentId().isBlank()
                ? "TXN-" + System.currentTimeMillis()
                : request.getRazorpayPaymentId());
            payment.setGatewayOrderId(orderId);
            payment.setGatewayResponse(request.getGatewayResponse() == null || request.getGatewayResponse().isBlank()
                ? "Payment verified through Razorpay"
                : request.getGatewayResponse());
            payment.setStatus(PaymentStatus.PAID);
            RuntimeException bookingConfirmationException = confirmBookingWithRetry(payment.getBookingId());
            if (bookingConfirmationException != null) {
                payment.setGatewayResponse(buildGatewayFailureMessage(
                    "Payment verified. Booking confirmation is pending",
                    bookingConfirmationException
                ));
            }
            publishPaymentEvent(payment, true);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setGatewayResponse("Payment signature verification failed");
            cancelBookingSilently(payment.getBookingId());
            publishPaymentEvent(payment, false);
        }

        return toResponse(paymentRepository.save(payment));
    }

    @Override
    @Transactional
    public PaymentResponse processPayment(ProcessPaymentRequest request) {
        Payment payment = findPayment(request.getPaymentId());

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new BadRequestException("Refunded payment cannot be processed again");
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new BadRequestException("Paid payment cannot be processed again");
        }

        payment.setTransactionId(request.getTransactionId() == null || request.getTransactionId().isBlank()
            ? "TXN-" + System.currentTimeMillis()
            : request.getTransactionId());
        payment.setGatewayResponse(request.getGatewayResponse());

        if (request.isSuccess()) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());

            RuntimeException bookingConfirmationException = confirmBookingWithRetry(payment.getBookingId());
            if (bookingConfirmationException != null) {
                payment.setGatewayResponse(buildGatewayFailureMessage(
                    request.getGatewayResponse(),
                    bookingConfirmationException
                ));
            }
            publishPaymentEvent(payment, true);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            cancelBookingSilently(payment.getBookingId());
            publishPaymentEvent(payment, false);
        }

        return toResponse(paymentRepository.save(payment));
    }

    @Override
    @Transactional
    public PaymentResponse refundPayment(RefundPaymentRequest request) {
        Payment payment = findPayment(request.getPaymentId());

        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BadRequestException("Only paid payments can be refunded");
        }
        if (request.getAmount().compareTo(payment.getAmount()) > 0) {
            throw new BadRequestException("Refund amount cannot exceed paid amount");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAmount(request.getAmount());
        payment.setRefundedAt(LocalDateTime.now());

        bookingServiceClient.updateBookingStatus(payment.getBookingId(), "CANCELLED");

        return toResponse(paymentRepository.save(payment));
    }

    private Payment newPayment(InitiatePaymentRequest request, String bookingId) {
        Payment payment = new Payment();
        payment.setPaymentId(UUID.randomUUID().toString());
        payment.setBookingId(bookingId);
        payment.setUserId(request.getUserId() == null ? 0L : request.getUserId());
        payment.setAmount(request.getAmount());
        payment.setCurrency(normalizeCurrency(request.getCurrency()));
        payment.setPaymentMode(request.getPaymentMode() == null
            ? com.skybooker.paymentservice.entity.PaymentMode.UPI
            : request.getPaymentMode());
        payment.setGatewayOrderId(null);
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }

    private Payment prepareExistingPayment(Payment payment, InitiatePaymentRequest request) {
        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment;
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new BadRequestException("Refunded booking cannot be paid again");
        }

        payment.setAmount(request.getAmount() == null ? payment.getAmount() : request.getAmount());
        payment.setCurrency(normalizeCurrency(request.getCurrency() == null ? payment.getCurrency() : request.getCurrency()));
        payment.setUserId(request.getUserId() == null ? payment.getUserId() : request.getUserId());
        payment.setPaymentMode(request.getPaymentMode() == null ? payment.getPaymentMode() : request.getPaymentMode());
        payment.setGatewayOrderId(null);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionId(null);
        payment.setGatewayResponse(null);
        payment.setPaidAt(null);
        payment.setRefundedAt(null);
        payment.setRefundedAmount(null);
        return payment;
    }

    private String createGatewayOrderId(Payment payment) {
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Payment amount must be greater than zero");
        }

        if (razorpayKeyId.isBlank() || razorpayKeySecret.isBlank()) {
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(razorpayKeyId.trim(), razorpayKeySecret.trim(), StandardCharsets.UTF_8);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", toSubunits(payment.getAmount()));
        payload.put("currency", normalizeCurrency(payment.getCurrency()));
        payload.put("receipt", buildReceipt(payment));
        payload.put("notes", Map.of("bookingId", payment.getBookingId(), "paymentId", payment.getPaymentId()));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
        String url = razorpayApiBaseUrl + "/v1/orders";

        try {
            ResponseEntity<Map> response = razorpayRestTemplate.postForEntity(url, requestEntity, Map.class);
            Object orderIdRaw = response.getBody() == null ? null : response.getBody().get("id");
            String orderId = normalize(orderIdRaw == null ? null : orderIdRaw.toString());
            if (orderId.isBlank()) {
                throw new BadRequestException("Unable to initialize payment. Please try again.");
            }
            return orderId;
        } catch (RestClientException exception) {
            throw new BadRequestException("Unable to initialize payment gateway right now. Please try again.");
        }
    }

    private void publishPaymentEvent(Payment payment, boolean success) {
        if (rabbitTemplate == null || payment == null) {
            return;
        }

        BookingSnapshot bookingSnapshot = bookingServiceClient.getBookingById(payment.getBookingId());
        Long recipientId = payment.getUserId() != null && payment.getUserId() > 0
            ? payment.getUserId()
            : bookingSnapshot == null ? null : bookingSnapshot.getUserId();
        if (recipientId == null || recipientId <= 0) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipientId", recipientId);
        payload.put("eventName", success ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED");
        payload.put("type", "PAYMENT");
        payload.put("title", success ? "Payment Successful" : "Payment Failed");
        payload.put("message", success
            ? "Payment received. Your ticket is booked."
            : "Payment failed. Please retry to confirm your booking.");
        payload.put("channel", "APP");
        payload.put("channels", List.of("APP", "EMAIL"));
        payload.put("relatedBookingId", payment.getBookingId());

        if (bookingSnapshot != null) {
            if (bookingSnapshot.getFlightId() != null) {
                payload.put("relatedFlightId", bookingSnapshot.getFlightId());
            }
            if (bookingSnapshot.getPnrCode() != null && !bookingSnapshot.getPnrCode().isBlank()) {
                payload.put("pnrCode", bookingSnapshot.getPnrCode());
            }
            if (bookingSnapshot.getContactEmail() != null && !bookingSnapshot.getContactEmail().isBlank()) {
                payload.put("recipientEmail", bookingSnapshot.getContactEmail());
            }
            if (bookingSnapshot.getContactPhone() != null && !bookingSnapshot.getContactPhone().isBlank()) {
                payload.put("recipientPhone", bookingSnapshot.getContactPhone());
            }
        }

        rabbitTemplate.convertAndSend(
            bookingEventExchange,
            success ? "payment.success" : "payment.failed",
            payload
        );
    }

    private void cancelBookingSilently(String bookingId) {
        try {
            bookingServiceClient.updateBookingStatus(bookingId, "CANCELLED");
        } catch (RuntimeException ignored) {
            // Payment outcome remains the source of truth even if the compensating status call fails.
        }
    }

    private RuntimeException confirmBookingWithRetry(String bookingId) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= BOOKING_CONFIRMATION_MAX_ATTEMPTS; attempt++) {
            try {
                bookingServiceClient.updateBookingStatus(bookingId, "CONFIRMED");
                return null;
            } catch (RuntimeException exception) {
                lastException = exception;
                if (!isRetryableBookingFailure(exception) || attempt == BOOKING_CONFIRMATION_MAX_ATTEMPTS) {
                    break;
                }
                sleepBeforeRetry(attempt);
            }
        }
        return lastException;
    }

    private boolean isRetryableBookingFailure(RuntimeException exception) {
        if (exception instanceof HttpStatusCodeException statusCodeException) {
            return statusCodeException.getStatusCode().is5xxServerError();
        }
        return exception instanceof RestClientException;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(BOOKING_CONFIRMATION_RETRY_DELAY_MS * attempt);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildGatewayFailureMessage(String gatewayResponse, RuntimeException exception) {
        String prefix = gatewayResponse == null || gatewayResponse.isBlank() ? "Gateway approval could not be completed" : gatewayResponse;
        return prefix + ". Booking confirmation failed: " + exception.getMessage();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByBooking(String bookingId) {
        Payment payment = paymentRepository.findByBookingId(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found for booking: " + bookingId));
        return toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByUser(Long userId) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String getPaymentStatus(String paymentId) {
        return findPayment(paymentId).getStatus().name();
    }

    @Override
    @Transactional(readOnly = true)
    public String generateReceipt(String paymentId) {
        Payment payment = findPayment(paymentId);
        return "Receipt\n"
            + "Payment ID: " + payment.getPaymentId() + "\n"
            + "Booking ID: " + payment.getBookingId() + "\n"
            + "Amount: " + payment.getAmount() + " " + payment.getCurrency() + "\n"
            + "Status: " + payment.getStatus() + "\n"
            + "Mode: " + payment.getPaymentMode() + "\n"
            + "Txn ID: " + (payment.getTransactionId() == null ? "NA" : payment.getTransactionId()) + "\n";
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getRevenue(LocalDate fromDate, LocalDate toDate) {
        return paymentRepository.findByPaidAtBetween(fromDate.atStartOfDay(), toDate.atTime(LocalTime.MAX))
            .stream()
            .filter(payment -> payment.getStatus() == PaymentStatus.PAID)
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Payment findPayment(String paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
    }

    private Payment findPaymentForVerification(VerifyPaymentRequest request) {
        String bookingId = normalize(request.getBookingId());
        String paymentId = normalize(request.getPaymentId());

        Payment paymentByBooking = paymentRepository.findByBookingId(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found for booking: " + bookingId));

        if (!paymentId.isBlank() && !paymentId.equals(paymentByBooking.getPaymentId())) {
            throw new BadRequestException("paymentId does not match the provided bookingId");
        }

        return paymentByBooking;
    }

    private PaymentResponse toResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setBookingId(payment.getBookingId());
        response.setUserId(payment.getUserId());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setStatus(payment.getStatus());
        response.setPaymentMode(payment.getPaymentMode());
        response.setRazorpayKeyId(razorpayKeyId);
        response.setGatewayOrderId(payment.getGatewayOrderId());
        response.setTransactionId(payment.getTransactionId());
        response.setGatewayResponse(payment.getGatewayResponse());
        response.setCreatedAt(payment.getCreatedAt());
        response.setPaidAt(payment.getPaidAt());
        response.setRefundedAt(payment.getRefundedAt());
        response.setRefundedAmount(payment.getRefundedAmount());
        return response;
    }

    private boolean verifySignatureIfConfigured(String orderId, String paymentId, String signature) {
        if (paymentId == null || paymentId.isBlank()) {
            return false;
        }

        if (razorpayKeySecret.isBlank()) {
            return true;
        }

        if (orderId == null || orderId.isBlank()) {
            return false;
        }

        if (signature == null || signature.isBlank()) {
            return false;
        }

        try {
            String payload = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpayKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expectedSignature = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return expectedSignature.equalsIgnoreCase(signature);
        } catch (Exception exception) {
            throw new BadRequestException("Unable to verify payment signature");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeCurrency(String currency) {
        String normalized = normalize(currency).toUpperCase();
        return normalized.isBlank() ? "INR" : normalized;
    }

    private long toSubunits(BigDecimal amount) {
        return amount
            .setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValue();
    }

    private String buildReceipt(Payment payment) {
        String receipt = "booking-" + payment.getBookingId();
        return receipt.length() <= 40 ? receipt : receipt.substring(0, 40);
    }
}
