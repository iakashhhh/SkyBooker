package com.skybooker.seatservice.controller;

import com.skybooker.seatservice.dto.ConfirmSeatRequest;
import com.skybooker.seatservice.dto.HoldSeatRequest;
import com.skybooker.seatservice.dto.ReleaseSeatRequest;
import com.skybooker.seatservice.dto.SeatActionResponse;
import com.skybooker.seatservice.dto.SeatMapResponse;
import com.skybooker.seatservice.service.SeatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/seats")
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping("/{flightId}")
    public SeatMapResponse getSeatMap(@PathVariable Long flightId,
                                      @RequestParam(value = "totalSeats", required = false) Integer totalSeats) {
        return seatService.getSeatMap(flightId, totalSeats);
    }

    @PostMapping("/hold")
    public SeatActionResponse holdSeats(@Valid @RequestBody HoldSeatRequest request) {
        return seatService.holdSeats(request);
    }

    @PostMapping("/release")
    public SeatActionResponse releaseSeats(@Valid @RequestBody ReleaseSeatRequest request) {
        return seatService.releaseSeats(request);
    }

    @PostMapping("/confirm")
    public SeatActionResponse confirmSeats(@Valid @RequestBody ConfirmSeatRequest request) {
        return seatService.confirmSeats(request);
    }
}
