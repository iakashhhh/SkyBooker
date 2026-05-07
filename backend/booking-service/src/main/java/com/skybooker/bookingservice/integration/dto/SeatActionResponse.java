package com.skybooker.bookingservice.integration.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Seat-service action response contract.
 */
@Data
@NoArgsConstructor
public class SeatActionResponse {
    private String message;
    private Long flightId;
    private List<Long> seatIds;
    private LocalDateTime holdExpiresAt;
}
