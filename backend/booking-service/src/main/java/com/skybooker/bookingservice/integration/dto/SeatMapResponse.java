package com.skybooker.bookingservice.integration.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SeatMapResponse {
    private Long flightId;
    private List<SeatView> seats;

    @Data
    public static class SeatView {
        private Long seatId;
        private String seatNumber;
        private String seatClass;
        private Boolean hasExtraLegroom;
        private BigDecimal priceMultiplier;
        private String status;
    }
}
