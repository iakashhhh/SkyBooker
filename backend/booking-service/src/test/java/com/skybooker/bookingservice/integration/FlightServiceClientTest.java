package com.skybooker.bookingservice.integration;

import com.skybooker.bookingservice.exception.ConflictException;
import com.skybooker.bookingservice.exception.ResourceNotFoundException;
import com.skybooker.bookingservice.integration.dto.FlightResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlightServiceClientTest {

    @Test
    void shouldReturnFlightAndAdjustSeats() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        FlightServiceClient client = new FlightServiceClient(restTemplate, "http://flight-service");

        FlightResponse flight = new FlightResponse();
        flight.setFlightId(7L);
        when(restTemplate.getForObject("http://flight-service/flights/7", FlightResponse.class)).thenReturn(flight);
        doNothing().when(restTemplate).put("http://flight-service/flights/7/availability", java.util.Map.of("seatDelta", 2));

        assertEquals(7L, client.getFlightById(7L).getFlightId());
        client.adjustAvailableSeats(7L, 2);

        verify(restTemplate).put("http://flight-service/flights/7/availability", java.util.Map.of("seatDelta", 2));
    }

    @Test
    void shouldThrowWhenFlightMissingOrSeatSyncFails() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        FlightServiceClient client = new FlightServiceClient(restTemplate, "http://flight-service");

        when(restTemplate.getForObject("http://flight-service/flights/8", FlightResponse.class))
            .thenThrow(HttpClientErrorException.NotFound.class);

        assertThrows(ResourceNotFoundException.class, () -> client.getFlightById(8L));

        doThrow(HttpClientErrorException.NotFound.class)
            .when(restTemplate).put("http://flight-service/flights/9/availability", java.util.Map.of("seatDelta", 1));
        assertThrows(ResourceNotFoundException.class, () -> client.adjustAvailableSeats(9L, 1));

        doThrow(new RestClientException("down"))
            .when(restTemplate).put("http://flight-service/flights/10/availability", java.util.Map.of("seatDelta", 1));
        assertThrows(ConflictException.class, () -> client.adjustAvailableSeats(10L, 1));
    }

    @Test
    void shouldSkipAdjustmentWhenSeatDeltaZero() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        FlightServiceClient client = new FlightServiceClient(restTemplate, "http://flight-service");

        client.adjustAvailableSeats(77L, 0);

        verify(restTemplate, org.mockito.Mockito.never())
            .put(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }
}
