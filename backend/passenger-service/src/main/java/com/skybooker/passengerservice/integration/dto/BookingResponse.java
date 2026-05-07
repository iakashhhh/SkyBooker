package com.skybooker.passengerservice.integration.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BookingResponse {
    private String bookingId;
    private List<Long> seatIds;
}
