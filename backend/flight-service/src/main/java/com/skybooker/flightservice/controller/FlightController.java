package com.skybooker.flightservice.controller;

import com.skybooker.flightservice.dto.ApiMessageResponse;
import com.skybooker.flightservice.dto.CreateFlightRequest;
import com.skybooker.flightservice.dto.FlightResponse;
import com.skybooker.flightservice.dto.RoundTripSearchResponse;
import com.skybooker.flightservice.dto.UpdateFlightAvailabilityRequest;
import com.skybooker.flightservice.dto.UpdateFlightRequest;
import com.skybooker.flightservice.dto.UpdateFlightStatusRequest;
import com.skybooker.flightservice.entity.DepartureWindow;
import com.skybooker.flightservice.entity.SeatClass;
import com.skybooker.flightservice.exception.BadRequestException;
import com.skybooker.flightservice.service.FlightService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * This controller exposes all Day 3 APIs for flight management and search.
 * It follows clean REST style for create, read, update, delete, and search.
 */
@RestController
@RequestMapping("/flights")
public class FlightController {

    private final FlightService flightService;

    public FlightController(FlightService flightService) {
        this.flightService = flightService;
    }

    @GetMapping
    public List<FlightResponse> getManagedFlights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long airlineId) {
        return flightService.getManagedFlights(date, airlineId);
    }

    /**
     * Adds a new flight schedule.
     */
    @PostMapping
    public FlightResponse addFlight(@Valid @RequestBody CreateFlightRequest request) {
        return flightService.addFlight(request);
    }

    /**
     * Updates full flight details by ID.
     */
    @PutMapping("/{id}")
    public FlightResponse updateFlight(@PathVariable("id") Long id,
                                       @Valid @RequestBody UpdateFlightRequest request) {
        return flightService.updateFlight(id, request);
    }

    /**
     * Deletes one flight record by ID.
     */
    @DeleteMapping("/{id}")
    public ApiMessageResponse deleteFlight(@PathVariable("id") Long id) {
        return flightService.deleteFlight(id);
    }

    /**
     * Fetches one flight by ID.
     */
    @GetMapping("/{id}")
    public FlightResponse getFlightById(@PathVariable("id") Long id) {
        return flightService.getFlightById(id);
    }

    @PutMapping("/{id}/availability")
    public FlightResponse adjustAvailability(@PathVariable("id") Long id,
                                             @Valid @RequestBody UpdateFlightAvailabilityRequest request) {
        return flightService.adjustAvailableSeats(id, request.getSeatDelta());
    }

    /**
     * Updates operational status of a flight.
     */
    @PutMapping("/status")
    public FlightResponse updateFlightStatus(@Valid @RequestBody UpdateFlightStatusRequest request) {
        return flightService.updateFlightStatus(request);
    }

    /**
     * Performs one-way search with optional filters and sorting.
     */
    @GetMapping("/search")
    public List<FlightResponse> searchOneWayFlights(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam(name = "journeyDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journeyDate,
            @RequestParam(name = "departureDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Long airlineId,
            @RequestParam(required = false) DepartureWindow departureWindow,
            @RequestParam(required = false) Integer maxStops,
            @RequestParam(required = false) SeatClass seatClass,
            @RequestParam(required = false) String sortBy) {
        LocalDate effectiveJourneyDate = journeyDate != null ? journeyDate : departureDate;
        if (effectiveJourneyDate == null) {
            throw new BadRequestException("journeyDate or departureDate is required");
        }

        return flightService.searchOneWayFlights(
                origin,
                destination,
                effectiveJourneyDate,
                minPrice,
                maxPrice,
                airlineId,
                departureWindow,
                maxStops,
                seatClass,
                sortBy
        );
    }

    /**
     * Performs round-trip search by running onward and return queries.
     */
    @GetMapping("/search/round-trip")
    public RoundTripSearchResponse searchRoundTripFlights(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onwardDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Long airlineId,
            @RequestParam(required = false) DepartureWindow departureWindow,
            @RequestParam(required = false) Integer maxStops,
            @RequestParam(required = false) SeatClass seatClass,
            @RequestParam(required = false) String sortBy) {

        return flightService.searchRoundTripFlights(
                origin,
                destination,
                onwardDate,
                returnDate,
                minPrice,
                maxPrice,
                airlineId,
                departureWindow,
                maxStops,
                seatClass,
                sortBy
        );
    }
}
