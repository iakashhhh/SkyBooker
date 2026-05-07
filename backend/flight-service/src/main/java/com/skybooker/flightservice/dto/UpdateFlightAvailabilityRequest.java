package com.skybooker.flightservice.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload to adjust flight inventory after booking status transitions.
 */
public class UpdateFlightAvailabilityRequest {

    @NotNull
    private Integer seatDelta;

    public Integer getSeatDelta() {
        return seatDelta;
    }

    public void setSeatDelta(Integer seatDelta) {
        this.seatDelta = seatDelta;
    }
}
