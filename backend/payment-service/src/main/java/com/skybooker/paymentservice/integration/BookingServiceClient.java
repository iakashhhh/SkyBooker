package com.skybooker.paymentservice.integration;

import com.skybooker.paymentservice.dto.BookingStatusUpdateRequest;
import com.skybooker.paymentservice.integration.dto.BookingSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class BookingServiceClient {

    private final RestTemplate restTemplate;
    private final String bookingServiceBaseUrl;

    public BookingServiceClient(@Value("${booking.service.base-url:http://localhost:8080}") String bookingServiceBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.bookingServiceBaseUrl = bookingServiceBaseUrl;
    }

    public void updateBookingStatus(String bookingId, String status) {
        BookingStatusUpdateRequest request = new BookingStatusUpdateRequest();
        request.setStatus(status);

        HttpEntity<BookingStatusUpdateRequest> entity = new HttpEntity<>(request);
        String url = bookingServiceBaseUrl + "/api/v1/bookings/" + bookingId + "/status";
        restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
    }

    public BookingSnapshot getBookingById(String bookingId) {
        String url = bookingServiceBaseUrl + "/api/v1/bookings/" + bookingId;
        try {
            return restTemplate.getForObject(url, BookingSnapshot.class);
        } catch (RestClientException exception) {
            return null;
        }
    }
}
