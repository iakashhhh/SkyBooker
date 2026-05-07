package com.skybooker.seatservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request payload for releasing held seats.
 */
@Data
@NoArgsConstructor
public class ReleaseSeatRequest {

    @NotNull(message = "Flight ID is required")
    private Long flightId;

    @NotEmpty(message = "At least one seat ID is required")
    private List<Long> seatIds;
}
