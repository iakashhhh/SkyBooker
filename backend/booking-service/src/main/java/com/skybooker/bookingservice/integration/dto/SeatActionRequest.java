package com.skybooker.bookingservice.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Seat-service hold/release request contract.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatActionRequest {
    private Long flightId;
    private List<Long> seatIds;
}
