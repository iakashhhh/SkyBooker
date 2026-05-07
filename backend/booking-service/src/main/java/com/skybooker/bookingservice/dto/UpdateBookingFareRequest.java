package com.skybooker.bookingservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
public class UpdateBookingFareRequest {

    @NotNull
    @Min(0)
    private Integer luggageKg;

    @Valid
    private List<MealSelection> mealSelections;

    @Data
    @NoArgsConstructor
    public static class MealSelection {
        private Integer passengerIndex;
        private String mealId;
        private String mealName;
        @Min(0)
        private BigDecimal mealPrice;
    }
}
