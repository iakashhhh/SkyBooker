package com.skybooker.passengerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignSeatRequest {

    @NotNull
    private Long seatId;

    @NotBlank
    private String seatNumber;
}
