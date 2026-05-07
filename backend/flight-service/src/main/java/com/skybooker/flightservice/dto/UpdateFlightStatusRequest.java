package com.skybooker.flightservice.dto;

import com.skybooker.flightservice.entity.FlightStatus;
import jakarta.validation.constraints.NotNull;

/**
 * This DTO is used for lightweight flight status updates.
 * It avoids sending full flight payload for simple status changes.
 */
public class UpdateFlightStatusRequest {

    @NotNull(message = "Flight ID is required")
    private Long flightId;

    @NotNull(message = "Status is required")
    private FlightStatus status;

    public Long getFlightId() {
        return flightId;
    }

    public void setFlightId(Long flightId) {
        this.flightId = flightId;
    }

    public FlightStatus getStatus() {
        return status;
    }

    public void setStatus(FlightStatus status) {
        this.status = status;
    }
}
