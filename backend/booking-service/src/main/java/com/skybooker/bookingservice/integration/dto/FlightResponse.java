package com.skybooker.bookingservice.integration.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Partial flight-service contract used for booking validation.
 */
@Data
@NoArgsConstructor
public class FlightResponse {
    private Long flightId;
    private String flightNumber;
    private String originAirportCode;
    private String destinationAirportCode;
    private LocalDateTime departureTime;
    private Integer totalSeats;
    private Integer availableSeats;
    private BigDecimal displayedPrice;
}
