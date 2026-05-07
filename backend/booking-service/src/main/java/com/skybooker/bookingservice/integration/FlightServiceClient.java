package com.skybooker.bookingservice.integration;

import com.skybooker.bookingservice.integration.dto.FlightResponse;
import com.skybooker.bookingservice.exception.ConflictException;
import com.skybooker.bookingservice.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Lightweight HTTP client for flight-service validation calls.
 */
@Component
public class FlightServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public FlightServiceClient(RestTemplate restTemplate,
                               @Value("${integration.flight-service-base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public FlightResponse getFlightById(Long flightId) {
        try {
            return restTemplate.getForObject(baseUrl + "/flights/" + flightId, FlightResponse.class);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResourceNotFoundException("Flight not found with id: " + flightId);
        }
    }

    public void adjustAvailableSeats(Long flightId, int seatDelta) {
        if (seatDelta == 0) {
            return;
        }
        try {
            restTemplate.put(baseUrl + "/flights/" + flightId + "/availability", Map.of("seatDelta", seatDelta));
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResourceNotFoundException("Flight not found with id: " + flightId);
        } catch (RestClientException exception) {
            throw new ConflictException("Unable to sync flight seat inventory right now");
        }
    }
}
