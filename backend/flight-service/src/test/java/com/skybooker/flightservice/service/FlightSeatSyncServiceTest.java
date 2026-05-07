package com.skybooker.flightservice.service;

import com.skybooker.flightservice.entity.Flight;
import com.skybooker.flightservice.repository.FlightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightSeatSyncServiceTest {

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private RestTemplate restTemplate;

    private FlightSeatSyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new FlightSeatSyncService(flightRepository, restTemplate, "http://localhost:8080", 1000L);
    }

    @Test
    void syncSeats_shouldRecalculateAvailabilityForSeatConsumingStatuses() {
        Flight flight = new Flight();
        flight.setFlightId(77L);
        flight.setTotalSeats(120);
        flight.setAvailableSeats(120);

        when(restTemplate.getForObject("http://localhost:8080/api/v1/bookings", List.class)).thenReturn(List.of(
            Map.of("flightId", 77L, "status", "CONFIRMED", "seatIds", List.of(1, 2, 3)),
            Map.of("flightId", 77L, "status", "COMPLETED", "seatIds", List.of(4)),
            Map.of("flightId", 77L, "status", "CANCELLED", "seatIds", List.of(5))
        ));
        when(flightRepository.findAll()).thenReturn(List.of(flight));

        syncService.syncSeats();

        assertEquals(116, flight.getAvailableSeats());
        verify(flightRepository).saveAll(anyList());
    }

    @Test
    void syncSeats_shouldSkipSaveWhenNoAvailabilityChanges() {
        Flight flight = new Flight();
        flight.setFlightId(55L);
        flight.setTotalSeats(100);
        flight.setAvailableSeats(97);

        when(restTemplate.getForObject("http://localhost:8080/api/v1/bookings", List.class)).thenReturn(List.of(
            Map.of("flightId", 55L, "status", "CONFIRMED", "seatIds", List.of(10, 11, 12))
        ));
        when(flightRepository.findAll()).thenReturn(List.of(flight));

        syncService.syncSeats();

        verify(flightRepository, never()).saveAll(anyList());
    }

    @Test
    void syncSeats_shouldHandleUpstreamExceptionAsEmptyData() {
        Flight flight = new Flight();
        flight.setFlightId(88L);
        flight.setTotalSeats(100);
        flight.setAvailableSeats(100);

        when(restTemplate.getForObject("http://localhost:8080/api/v1/bookings", List.class))
            .thenThrow(new RuntimeException("booking service unavailable"));
        when(flightRepository.findAll()).thenReturn(List.of(flight));

        syncService.syncSeats();

        verify(flightRepository, never()).saveAll(anyList());
    }

    @Test
    void syncIfNeeded_shouldThrottleRepeatedCallsWithinInterval() {
        when(restTemplate.getForObject("http://localhost:8080/api/v1/bookings", List.class)).thenReturn(List.of());
        when(flightRepository.findAll()).thenReturn(List.of());

        syncService.syncIfNeeded();
        syncService.syncIfNeeded();

        verify(restTemplate, times(1)).getForObject("http://localhost:8080/api/v1/bookings", List.class);
    }
}
