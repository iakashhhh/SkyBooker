package com.skybooker.seatservice.controller;

import com.skybooker.seatservice.dto.ConfirmSeatRequest;
import com.skybooker.seatservice.dto.HoldSeatRequest;
import com.skybooker.seatservice.dto.ReleaseSeatRequest;
import com.skybooker.seatservice.dto.SeatActionResponse;
import com.skybooker.seatservice.dto.SeatMapResponse;
import com.skybooker.seatservice.service.SeatService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeatControllerTest {

    private final SeatService seatService = mock(SeatService.class);
    private final SeatController controller = new SeatController(seatService);

    @Test
    void shouldDelegateAllSeatEndpoints() {
        SeatMapResponse mapResponse = new SeatMapResponse();
        mapResponse.setFlightId(8L);

        HoldSeatRequest holdRequest = new HoldSeatRequest();
        holdRequest.setFlightId(8L);
        holdRequest.setSeatIds(List.of(1L, 2L));

        ReleaseSeatRequest releaseRequest = new ReleaseSeatRequest();
        releaseRequest.setFlightId(8L);
        releaseRequest.setSeatIds(List.of(1L));

        ConfirmSeatRequest confirmRequest = new ConfirmSeatRequest();
        confirmRequest.setFlightId(8L);
        confirmRequest.setSeatIds(List.of(1L));

        SeatActionResponse actionResponse = new SeatActionResponse("ok", 8L, List.of(1L), LocalDateTime.now().plusMinutes(5));

        when(seatService.getSeatMap(8L, 180)).thenReturn(mapResponse);
        when(seatService.holdSeats(holdRequest)).thenReturn(actionResponse);
        when(seatService.releaseSeats(releaseRequest)).thenReturn(actionResponse);
        when(seatService.confirmSeats(confirmRequest)).thenReturn(actionResponse);

        assertSame(mapResponse, controller.getSeatMap(8L, 180));
        assertSame(actionResponse, controller.holdSeats(holdRequest));
        assertSame(actionResponse, controller.releaseSeats(releaseRequest));
        assertSame(actionResponse, controller.confirmSeats(confirmRequest));

        verify(seatService).getSeatMap(8L, 180);
        verify(seatService).holdSeats(holdRequest);
        verify(seatService).releaseSeats(releaseRequest);
        verify(seatService).confirmSeats(confirmRequest);
    }
}
