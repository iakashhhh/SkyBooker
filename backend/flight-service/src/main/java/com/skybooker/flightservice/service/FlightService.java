package com.skybooker.flightservice.service;

import com.skybooker.flightservice.dto.ApiMessageResponse;
import com.skybooker.flightservice.dto.CreateFlightRequest;
import com.skybooker.flightservice.dto.FlightResponse;
import com.skybooker.flightservice.dto.RoundTripSearchResponse;
import com.skybooker.flightservice.dto.UpdateFlightRequest;
import com.skybooker.flightservice.dto.UpdateFlightStatusRequest;
import com.skybooker.flightservice.entity.DepartureWindow;
import com.skybooker.flightservice.entity.SeatClass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * This interface defines all flight management and search operations.
 * It keeps controller logic clean and business rules centralized.
 */
public interface FlightService {

    List<FlightResponse> getManagedFlights(LocalDate flightDate, Long airlineId);

    FlightResponse addFlight(CreateFlightRequest request);

    FlightResponse updateFlight(Long flightId, UpdateFlightRequest request);

    ApiMessageResponse deleteFlight(Long flightId);

    FlightResponse getFlightById(Long flightId);

    FlightResponse adjustAvailableSeats(Long flightId, Integer seatDelta);

    FlightResponse updateFlightStatus(UpdateFlightStatusRequest request);

    List<FlightResponse> searchOneWayFlights(String originAirportCode,
                                             String destinationAirportCode,
                                             LocalDate journeyDate,
                                             BigDecimal minPrice,
                                             BigDecimal maxPrice,
                                             Long airlineId,
                                             DepartureWindow departureWindow,
                                             Integer maxStops,
                                             SeatClass seatClass,
                                             String sortBy);

    RoundTripSearchResponse searchRoundTripFlights(String originAirportCode,
                                                   String destinationAirportCode,
                                                   LocalDate onwardDate,
                                                   LocalDate returnDate,
                                                   BigDecimal minPrice,
                                                   BigDecimal maxPrice,
                                                   Long airlineId,
                                                   DepartureWindow departureWindow,
                                                   Integer maxStops,
                                                   SeatClass seatClass,
                                                   String sortBy);
}
