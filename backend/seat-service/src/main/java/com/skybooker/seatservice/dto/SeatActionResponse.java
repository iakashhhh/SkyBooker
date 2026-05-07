package com.skybooker.seatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Generic response for hold, release, and confirm actions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatActionResponse {

    private String message;
    private Long flightId;
    private List<Long> seatIds;
    private LocalDateTime holdExpiresAt;
}
