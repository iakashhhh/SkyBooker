package com.skybooker.flightservice.service;

import com.skybooker.flightservice.dto.CreateFlightRequest;
import com.skybooker.flightservice.dto.FlightResponse;
import com.skybooker.flightservice.dto.RoundTripSearchResponse;
import com.skybooker.flightservice.dto.UpdateFlightStatusRequest;
import com.skybooker.flightservice.entity.DepartureWindow;
import com.skybooker.flightservice.entity.Flight;
import com.skybooker.flightservice.entity.FlightStatus;
import com.skybooker.flightservice.entity.SeatClass;
import com.skybooker.flightservice.exception.BadRequestException;
import com.skybooker.flightservice.exception.ResourceNotFoundException;
import com.skybooker.flightservice.repository.FlightRepository;
import com.skybooker.flightservice.service.AirlineCatalogClient;
import com.skybooker.flightservice.service.impl.FlightServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Day 3 flight business logic.
 * These tests focus on search filters and key create validations.
 */
@ExtendWith(MockitoExtension.class)
class FlightServiceImplTest {

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private AirlineCatalogClient airlineCatalogClient;

    @InjectMocks
    private FlightServiceImpl flightService;

    private Flight sampleFlight;

    @BeforeEach
    void setUp() {
        sampleFlight = new Flight();
        sampleFlight.setFlightId(10L);
        sampleFlight.setFlightNumber("AI-101");
        sampleFlight.setAirlineId(501L);
        sampleFlight.setOriginAirportCode("DEL");
        sampleFlight.setDestinationAirportCode("BLR");
        sampleFlight.setDepartureTime(LocalDateTime.of(2026, 1, 15, 9, 30));
        sampleFlight.setArrivalTime(LocalDateTime.of(2026, 1, 15, 12, 15));
        sampleFlight.setDurationMinutes(165);
        sampleFlight.setStatus(FlightStatus.ON_TIME);
        sampleFlight.setAircraftType("Airbus A320");
        sampleFlight.setTotalSeats(180);
        sampleFlight.setAvailableSeats(55);
        sampleFlight.setBasePrice(new BigDecimal("5000.00"));
    }

    @Test
    void addFlight_shouldThrowException_whenArrivalBeforeDeparture() {
        CreateFlightRequest request = new CreateFlightRequest();
        request.setFlightNumber("AI-220");
        request.setAirlineId(1001L);
        request.setOriginAirportCode("DEL");
        request.setDestinationAirportCode("BOM");
        request.setDepartureTime(LocalDateTime.of(2026, 2, 10, 14, 0));
        request.setArrivalTime(LocalDateTime.of(2026, 2, 10, 13, 0));
        request.setDurationMinutes(120);
        request.setStatus(FlightStatus.ON_TIME);
        request.setAircraftType("Boeing 737");
        request.setTotalSeats(150);
        request.setAvailableSeats(120);
        request.setBasePrice(new BigDecimal("4200"));

        assertThrows(BadRequestException.class, () -> flightService.addFlight(request));
        verify(flightRepository, never()).save(ArgumentMatchers.any(Flight.class));
    }

    @Test
    void searchOneWayFlights_shouldApplySeatClassPricing_andSortByDepartureDesc() {
        when(flightRepository.findAll(ArgumentMatchers.<Specification<Flight>>any()))
                .thenReturn(List.of(sampleFlight));

        List<FlightResponse> results = flightService.searchOneWayFlights(
                "DEL",
                "BLR",
                LocalDate.of(2026, 1, 15),
                null,
                null,
                null,
                DepartureWindow.MORNING,
                0,
                SeatClass.BUSINESS,
                "departure_desc"
        );

        assertEquals(1, results.size());
        assertEquals("AI-101", results.get(0).getFlightNumber());
        assertEquals(new BigDecimal("8500.00"), results.get(0).getDisplayedPrice());
    }

    @Test
    void searchOneWayFlights_shouldFilterByPriceRange() {
        when(flightRepository.findAll(ArgumentMatchers.<Specification<Flight>>any()))
                .thenReturn(List.of(sampleFlight));

        List<FlightResponse> emptyResults = flightService.searchOneWayFlights(
                "DEL",
                "BLR",
                LocalDate.of(2026, 1, 15),
                new BigDecimal("9000"),
                new BigDecimal("12000"),
                null,
                null,
                null,
                SeatClass.ECONOMY,
                "price_desc"
        );

        assertEquals(0, emptyResults.size());
    }

    @Test
    void searchRoundTripFlights_shouldReturnBothDirections() {
        Flight returnFlight = new Flight();
        returnFlight.setFlightId(11L);
        returnFlight.setFlightNumber("AI-102");
        returnFlight.setAirlineId(501L);
        returnFlight.setOriginAirportCode("BLR");
        returnFlight.setDestinationAirportCode("DEL");
        returnFlight.setDepartureTime(LocalDateTime.of(2026, 1, 20, 19, 10));
        returnFlight.setArrivalTime(LocalDateTime.of(2026, 1, 20, 21, 45));
        returnFlight.setDurationMinutes(155);
        returnFlight.setStatus(FlightStatus.ON_TIME);
        returnFlight.setAircraftType("Airbus A321");
        returnFlight.setTotalSeats(180);
        returnFlight.setAvailableSeats(80);
        returnFlight.setBasePrice(new BigDecimal("5200.00"));

        when(flightRepository.findAll(ArgumentMatchers.<Specification<Flight>>any()))
                .thenReturn(List.of(sampleFlight))
                .thenReturn(List.of(returnFlight));

        RoundTripSearchResponse response = flightService.searchRoundTripFlights(
                "DEL",
                "BLR",
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 20),
                null,
                null,
                null,
                null,
                null,
                SeatClass.ECONOMY,
                "price_desc"
        );

        assertEquals(1, response.getOutboundFlights().size());
        assertEquals(1, response.getReturnFlights().size());
        assertEquals("AI-101", response.getOutboundFlights().get(0).getFlightNumber());
        assertEquals("AI-102", response.getReturnFlights().get(0).getFlightNumber());
    }

    @Test
    void searchOneWayFlights_shouldRejectNegativeMaxStops() {
        assertThrows(BadRequestException.class, () -> flightService.searchOneWayFlights(
            "DEL",
            "BLR",
            LocalDate.of(2026, 1, 15),
            null,
            null,
            null,
            null,
            -1,
            SeatClass.ECONOMY,
            "price_asc"
        ));
    }

    @Test
    void adjustAvailableSeats_shouldUpdateAvailabilityForValidDelta() {
        when(flightRepository.findById(10L)).thenReturn(java.util.Optional.of(sampleFlight));
        when(flightRepository.save(ArgumentMatchers.any(Flight.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FlightResponse response = flightService.adjustAvailableSeats(10L, -5);

        assertEquals(50, response.getAvailableSeats());
    }

    @Test
    void adjustAvailableSeats_shouldRejectWhenAvailabilityGoesBelowZero() {
        sampleFlight.setAvailableSeats(3);
        when(flightRepository.findById(10L)).thenReturn(java.util.Optional.of(sampleFlight));

        assertThrows(BadRequestException.class, () -> flightService.adjustAvailableSeats(10L, -5));
    }

    @Test
    void adjustAvailableSeats_shouldRejectWhenAvailabilityExceedsTotalSeats() {
        sampleFlight.setAvailableSeats(175);
        sampleFlight.setTotalSeats(180);
        when(flightRepository.findById(10L)).thenReturn(java.util.Optional.of(sampleFlight));

        assertThrows(BadRequestException.class, () -> flightService.adjustAvailableSeats(10L, 10));
    }

    @Test
    void addFlight_shouldPersistFlightWhenRequestIsValid() {
        CreateFlightRequest request = new CreateFlightRequest();
        request.setFlightNumber("AI-222");
        request.setAirlineId(1001L);
        request.setOriginAirportCode("DEL");
        request.setDestinationAirportCode("BOM");
        request.setDepartureTime(LocalDateTime.of(2026, 2, 10, 14, 0));
        request.setArrivalTime(LocalDateTime.of(2026, 2, 10, 16, 0));
        request.setDurationMinutes(120);
        request.setNumberOfStops(0);
        request.setStatus(FlightStatus.ON_TIME);
        request.setAircraftType("Boeing 737");
        request.setTotalSeats(150);
        request.setAvailableSeats(120);
        request.setBasePrice(new BigDecimal("4200"));

        when(flightRepository.existsByFlightNumber("AI-222")).thenReturn(false);
        when(flightRepository.save(ArgumentMatchers.any(Flight.class))).thenAnswer(invocation -> {
            Flight flight = invocation.getArgument(0);
            flight.setFlightId(20L);
            return flight;
        });

        FlightResponse response = flightService.addFlight(request);

        assertEquals("AI-222", response.getFlightNumber());
        assertEquals(20L, response.getFlightId());
        verify(airlineCatalogClient).validateAirlineExists(1001L);
    }

    @Test
    void updateFlight_shouldRejectDuplicateFlightNumber() {
        com.skybooker.flightservice.dto.UpdateFlightRequest request = new com.skybooker.flightservice.dto.UpdateFlightRequest();
        request.setFlightNumber("AI-101");
        request.setAirlineId(501L);
        request.setOriginAirportCode("DEL");
        request.setDestinationAirportCode("BLR");
        request.setDepartureTime(LocalDateTime.of(2026, 2, 10, 9, 0));
        request.setArrivalTime(LocalDateTime.of(2026, 2, 10, 11, 0));
        request.setDurationMinutes(120);
        request.setNumberOfStops(0);
        request.setStatus(FlightStatus.ON_TIME);
        request.setAircraftType("A320");
        request.setTotalSeats(180);
        request.setAvailableSeats(90);
        request.setBasePrice(new BigDecimal("4100"));

        Flight existingOther = new Flight();
        existingOther.setFlightId(99L);
        existingOther.setFlightNumber("AI-101");

        when(flightRepository.findById(10L)).thenReturn(java.util.Optional.of(sampleFlight));
        when(flightRepository.findByFlightNumber("AI-101")).thenReturn(java.util.Optional.of(existingOther));

        assertThrows(BadRequestException.class, () -> flightService.updateFlight(10L, request));
    }

    @Test
    void deleteFlight_shouldRemoveExistingFlight() {
        when(flightRepository.findById(10L)).thenReturn(java.util.Optional.of(sampleFlight));

        var response = flightService.deleteFlight(10L);

        assertEquals("Flight deleted successfully", response.getMessage());
        verify(flightRepository).delete(sampleFlight);
    }

    @Test
    void getFlightById_shouldThrowWhenMissing() {
        when(flightRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> flightService.getFlightById(999L));
    }

    @Test
    void searchRoundTripFlights_shouldRejectWhenReturnDateMissing() {
        assertThrows(BadRequestException.class, () -> flightService.searchRoundTripFlights(
            "DEL",
            "BLR",
            LocalDate.of(2026, 1, 15),
            null,
            null,
            null,
            null,
            null,
            null,
            SeatClass.ECONOMY,
            "price_asc"
        ));
    }

    @Test
    void addFlight_shouldRejectWhenOriginAndDestinationAreSame() {
        CreateFlightRequest request = new CreateFlightRequest();
        request.setFlightNumber("AI-333");
        request.setAirlineId(1001L);
        request.setOriginAirportCode("DEL");
        request.setDestinationAirportCode("DEL");
        request.setDepartureTime(LocalDateTime.of(2026, 2, 10, 14, 0));
        request.setArrivalTime(LocalDateTime.of(2026, 2, 10, 16, 0));
        request.setDurationMinutes(120);
        request.setNumberOfStops(0);
        request.setStatus(FlightStatus.ON_TIME);
        request.setAircraftType("Boeing 737");
        request.setTotalSeats(150);
        request.setAvailableSeats(120);
        request.setBasePrice(new BigDecimal("4200"));

        assertThrows(BadRequestException.class, () -> flightService.addFlight(request));
    }

    @Test
    void getFlightById_shouldReturnFlightForValidId() {
        when(flightRepository.findById(10L)).thenReturn(java.util.Optional.of(sampleFlight));

        FlightResponse response = flightService.getFlightById(10L);

        assertEquals("AI-101", response.getFlightNumber());
        assertEquals(10L, response.getFlightId());
    }

    @Test
    void updateFlightStatus_shouldPersistNewStatus() {
        when(flightRepository.findById(10L)).thenReturn(java.util.Optional.of(sampleFlight));
        when(flightRepository.save(ArgumentMatchers.any(Flight.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateFlightStatusRequest request = new UpdateFlightStatusRequest();
        request.setFlightId(10L);
        request.setStatus(FlightStatus.CANCELLED);

        FlightResponse response = flightService.updateFlightStatus(request);

        assertEquals(FlightStatus.CANCELLED, response.getStatus());
    }

    @Test
    void adjustAvailableSeats_shouldReturnUnchangedWhenDeltaNull() {
        when(flightRepository.findById(10L)).thenReturn(java.util.Optional.of(sampleFlight));

        FlightResponse response = flightService.adjustAvailableSeats(10L, null);

        assertEquals(55, response.getAvailableSeats());
        verify(flightRepository, never()).save(ArgumentMatchers.any(Flight.class));
    }

    @Test
    void addFlight_shouldRejectWhenStopsPositiveButViaMissing() {
        CreateFlightRequest request = new CreateFlightRequest();
        request.setFlightNumber("AI-334");
        request.setAirlineId(1001L);
        request.setOriginAirportCode("DEL");
        request.setDestinationAirportCode("BLR");
        request.setDepartureTime(LocalDateTime.of(2026, 2, 10, 14, 0));
        request.setArrivalTime(LocalDateTime.of(2026, 2, 10, 16, 0));
        request.setDurationMinutes(120);
        request.setNumberOfStops(1);
        request.setViaAirportCode(null);
        request.setStatus(FlightStatus.ON_TIME);
        request.setAircraftType("Boeing 737");
        request.setTotalSeats(150);
        request.setAvailableSeats(120);
        request.setBasePrice(new BigDecimal("4200"));

        assertThrows(BadRequestException.class, () -> flightService.addFlight(request));
    }

    @Test
    void getManagedFlights_shouldFilterByDateAndReturnSortedList() {
        Flight second = new Flight();
        second.setFlightId(12L);
        second.setFlightNumber("AI-050");
        second.setAirlineId(501L);
        second.setOriginAirportCode("DEL");
        second.setDestinationAirportCode("BLR");
        second.setDepartureTime(LocalDateTime.of(2026, 1, 15, 7, 0));
        second.setArrivalTime(LocalDateTime.of(2026, 1, 15, 9, 0));
        second.setDurationMinutes(120);
        second.setStatus(FlightStatus.ON_TIME);
        second.setAircraftType("A320");
        second.setTotalSeats(180);
        second.setAvailableSeats(100);
        second.setBasePrice(new BigDecimal("4500"));

        when(flightRepository.findAll(ArgumentMatchers.<Specification<Flight>>any()))
            .thenReturn(List.of(sampleFlight, second));

        List<FlightResponse> responses = flightService.getManagedFlights(LocalDate.of(2026, 1, 15), 501L);

        assertEquals(2, responses.size());
        assertEquals("AI-050", responses.get(0).getFlightNumber());
    }
}
