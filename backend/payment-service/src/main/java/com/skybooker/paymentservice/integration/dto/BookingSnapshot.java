package com.skybooker.paymentservice.integration.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookingSnapshot {
    private String bookingId;
    private Long userId;
    private Long flightId;
    private String pnrCode;
    private String contactEmail;
    private String contactPhone;
}
