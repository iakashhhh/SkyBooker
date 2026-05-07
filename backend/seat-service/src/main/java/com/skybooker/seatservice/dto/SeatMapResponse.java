package com.skybooker.seatservice.dto;

import com.skybooker.seatservice.entity.SeatClass;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Full seat map response with availability summary.
 */
@Data
@NoArgsConstructor
public class SeatMapResponse {

    private Long flightId;
    private List<SeatResponse> seats;
    private Map<SeatClass, Long> availableSeatsByClass;
}
