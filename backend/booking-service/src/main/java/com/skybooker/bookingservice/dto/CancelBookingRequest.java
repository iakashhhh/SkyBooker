package com.skybooker.bookingservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for cancelling a booking.
 */
@Data
@NoArgsConstructor
public class CancelBookingRequest {

    @NotBlank
    private String bookingId;
}
