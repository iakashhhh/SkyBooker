package com.skybooker.bookingservice.integration;

import com.skybooker.bookingservice.dto.PaymentRequest;
import com.skybooker.bookingservice.dto.PaymentResponse;
import com.skybooker.bookingservice.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceClientTest {

    @Test
    void shouldReturnPendingWhenPaymentIntegrationDisabled() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        PaymentServiceClient client = new PaymentServiceClient(restTemplate, "http://payment-service", false);

        PaymentResponse response = client.createPayment(new PaymentRequest());

        assertEquals("PAY-PENDING", response.getPaymentId());
        assertEquals("PENDING", response.getStatus());
    }

    @Test
    void shouldThrowWhenGatewayReturnsInvalidPayload() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        PaymentServiceClient client = new PaymentServiceClient(restTemplate, "http://payment-service", true);

        PaymentResponse invalid = new PaymentResponse();
        invalid.setPaymentId(" ");
        when(restTemplate.postForObject(eq("http://payment-service/payments/initiate"), any(), eq(PaymentResponse.class)))
            .thenReturn(invalid);

        assertThrows(ConflictException.class, () -> client.createPayment(new PaymentRequest()));
    }

    @Test
    void shouldWrapGatewayExceptionAsConflict() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        PaymentServiceClient client = new PaymentServiceClient(restTemplate, "http://payment-service", true);

        when(restTemplate.postForObject(eq("http://payment-service/payments/initiate"), any(), eq(PaymentResponse.class)))
            .thenThrow(new RestClientException("down"));

        assertThrows(ConflictException.class, () -> client.createPayment(new PaymentRequest()));
    }
}
