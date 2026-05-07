package com.skybooker.passengerservice.integration;

import com.skybooker.passengerservice.exception.BadRequestException;
import com.skybooker.passengerservice.integration.dto.BookingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class BookingServiceClient {

    private final RestTemplate restTemplate;
    private final String bookingServiceBaseUrl;

    public BookingServiceClient(RestTemplate restTemplate,
                                @Value("${integration.booking-service-base-url:http://api-gateway:8080}") String bookingServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.bookingServiceBaseUrl = bookingServiceBaseUrl;
    }

    public BookingResponse getBookingById(String bookingId) {
        try {
            BookingResponse response = restTemplate.getForObject(
                bookingServiceBaseUrl + "/api/v1/bookings/" + bookingId,
                BookingResponse.class
            );
            if (response == null || response.getBookingId() == null || response.getBookingId().isBlank()) {
                throw new BadRequestException("Invalid booking reference: " + bookingId);
            }
            return response;
        } catch (RestClientException exception) {
            throw new BadRequestException("Unable to validate booking reference: " + bookingId);
        }
    }
}
