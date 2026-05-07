package com.skybooker.bookingservice.integration;

import com.skybooker.bookingservice.dto.PaymentRequest;
import com.skybooker.bookingservice.dto.PaymentResponse;
import com.skybooker.bookingservice.exception.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Payment integration client with graceful disabled mode.
 */
@Component
public class PaymentServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final boolean paymentEnabled;

    public PaymentServiceClient(RestTemplate restTemplate,
                                @Value("${integration.payment-service-base-url}") String baseUrl,
                                @Value("${integration.payment-enabled:true}") boolean paymentEnabled) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.paymentEnabled = paymentEnabled;
    }

    public PaymentResponse createPayment(PaymentRequest request) {
        if (!paymentEnabled) {
            PaymentResponse response = new PaymentResponse();
            response.setPaymentId("PAY-PENDING");
            response.setStatus("PENDING");
            return response;
        }

        try {
            PaymentResponse response = restTemplate.postForObject(baseUrl + "/payments/initiate", request, PaymentResponse.class);
            if (response == null || response.getPaymentId() == null || response.getPaymentId().isBlank()) {
                throw new ConflictException("Payment service returned an invalid response");
            }
            return response;
        } catch (RestClientException exception) {
            throw new ConflictException("Payment service is unavailable");
        }
    }
}
