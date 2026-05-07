package com.skybooker.bookingservice.dto;

import com.skybooker.bookingservice.entity.BookingStatus;
import com.skybooker.bookingservice.entity.TripType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response payload for booking APIs.
 */
@Data
@NoArgsConstructor
public class BookingResponse {

    private String bookingId;
    private Long userId;
    private Long flightId;
    private List<Long> seatIds;
    private String pnrCode;
    private TripType tripType;
    private BookingStatus status;
    private BigDecimal totalFare;
    private BigDecimal baseFare;
    private BigDecimal seatCharge;
    private BigDecimal baggageCharge;
    private BigDecimal mealCharge;
    private BigDecimal taxes;
    private String mealPreference;
    private Integer luggageKg;
    private String contactEmail;
    private String contactPhone;
    private LocalDateTime bookedAt;
    private String paymentId;
}
