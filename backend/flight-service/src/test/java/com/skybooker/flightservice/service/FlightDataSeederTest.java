package com.skybooker.flightservice.service;

import com.skybooker.flightservice.entity.Flight;
import com.skybooker.flightservice.repository.FlightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightDataSeederTest {

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private AirlineCatalogClient airlineCatalogClient;

    private FlightDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new FlightDataSeeder(flightRepository, airlineCatalogClient);
        seeder.setSeedData(true);
    }

    @Test
    void run_shouldSkipWhenSeedDataFlagIsDisabled() {
        seeder.setSeedData(false);

        seeder.run();

        verify(airlineCatalogClient, never()).fetchAirlines();
        verify(flightRepository, never()).saveAll(any());
    }

    @Test
    void run_shouldGenerateFlightsOnlyForActiveAirlinesWithinWindow() {
        when(flightRepository.deleteByDepartureTimeBefore(any())).thenReturn(0L);
        when(flightRepository.deleteByDepartureTimeAfter(any())).thenReturn(0L);
        when(flightRepository.existsByFlightNumber(anyString())).thenReturn(false);
        when(flightRepository.existsByAirlineIdAndOriginAirportCodeAndDestinationAirportCodeAndDepartureTime(any(), anyString(), anyString(), any()))
            .thenReturn(false);
        when(airlineCatalogClient.fetchAirlines()).thenReturn(List.of(
            Map.of("iataCode", "AI", "airlineId", 101L, "active", true),
            Map.of("iataCode", "ZZ", "airlineId", 202L, "active", false)
        ));
        when(flightRepository.findAll()).thenReturn(List.of());
        when(flightRepository.findByDepartureTimeBetween(any(), any())).thenReturn(List.of());

        seeder.run();

        ArgumentCaptor<List<Flight>> captor = ArgumentCaptor.forClass(List.class);
        verify(flightRepository).saveAll(captor.capture());

        List<Flight> generated = captor.getValue();
        assertFalse(generated.isEmpty());

        LocalDate today = LocalDate.now();
        LocalDate maxDate = today.plusDays(40);

        for (Flight flight : generated) {
            assertEquals(101L, flight.getAirlineId());
            assertNotNull(flight.getDepartureTime());
            assertFalse(flight.getOriginAirportCode().equals(flight.getDestinationAirportCode()));

            LocalDate departureDate = flight.getDepartureTime().toLocalDate();
            boolean inWindow = !departureDate.isBefore(today) && !departureDate.isAfter(maxDate);
            assertEquals(true, inWindow);
        }
    }

    @Test
    void run_shouldTrimRouteOverflowAndCleanupOutsideWindow() {
        when(flightRepository.deleteByDepartureTimeBefore(any())).thenReturn(0L);
        when(flightRepository.deleteByDepartureTimeAfter(any())).thenReturn(0L);
        when(flightRepository.existsByFlightNumber(anyString())).thenReturn(false);
        when(flightRepository.existsByAirlineIdAndOriginAirportCodeAndDestinationAirportCodeAndDepartureTime(any(), anyString(), anyString(), any()))
            .thenReturn(false);
        when(airlineCatalogClient.fetchAirlines()).thenReturn(List.of(
            Map.of("iataCode", "AI", "airlineId", 101L, "isActive", true)
        ));

        List<Flight> existingFlights = new ArrayList<>();
        LocalDate travelDate = LocalDate.now().plusDays(1);
        for (int i = 0; i < 5; i += 1) {
            Flight flight = new Flight();
            flight.setFlightId((long) (i + 1));
            flight.setFlightNumber("AI-" + (500 + i));
            flight.setAirlineId(101L);
            flight.setOriginAirportCode("DEL");
            flight.setDestinationAirportCode("MUM");
            flight.setDepartureTime(LocalDateTime.of(travelDate, LocalTime.of(6 + i, 0)));
            existingFlights.add(flight);
        }

        when(flightRepository.findAll()).thenReturn(existingFlights);
        when(flightRepository.findByDepartureTimeBetween(any(), any())).thenReturn(existingFlights);

        seeder.run();

        verify(flightRepository).deleteByDepartureTimeBefore(any());
        verify(flightRepository).deleteByDepartureTimeAfter(any());

        ArgumentCaptor<List<Flight>> deleteCaptor = ArgumentCaptor.forClass(List.class);
        verify(flightRepository).deleteAllInBatch(deleteCaptor.capture());
        assertEquals(2, deleteCaptor.getValue().size());
    }
}
