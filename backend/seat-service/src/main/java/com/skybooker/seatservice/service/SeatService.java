package com.skybooker.seatservice.service;

import com.skybooker.seatservice.dto.ConfirmSeatRequest;
import com.skybooker.seatservice.dto.HoldSeatRequest;
import com.skybooker.seatservice.dto.ReleaseSeatRequest;
import com.skybooker.seatservice.dto.SeatActionResponse;
import com.skybooker.seatservice.dto.SeatMapResponse;

/**
 * Seat lifecycle contract for hold, release, and confirmation.
 */
public interface SeatService {

    SeatMapResponse getSeatMap(Long flightId, Integer totalSeats);

    SeatActionResponse holdSeats(HoldSeatRequest request);

    SeatActionResponse releaseSeats(ReleaseSeatRequest request);

    SeatActionResponse confirmSeats(ConfirmSeatRequest request);

    void releaseExpiredHolds();
}
