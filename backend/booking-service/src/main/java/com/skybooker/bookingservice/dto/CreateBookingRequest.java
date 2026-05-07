package com.skybooker.bookingservice.dto;

import com.skybooker.bookingservice.entity.TripType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request payload for creating a booking.
 */
@Data
@NoArgsConstructor
public class CreateBookingRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Long flightId;

    @NotNull
    private TripType tripType;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal baseFare;

    @NotEmpty
    private List<Long> seatIds;

    private String mealPreference;

    @NotNull
    @Min(0)
    private Integer luggageKg;

    @NotNull
    @Email
    private String contactEmail;

    @NotNull
    private String contactPhone;
}
