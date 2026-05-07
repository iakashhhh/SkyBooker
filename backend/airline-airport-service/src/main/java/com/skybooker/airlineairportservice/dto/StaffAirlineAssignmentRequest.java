package com.skybooker.airlineairportservice.dto;

import jakarta.validation.constraints.NotNull;

public class StaffAirlineAssignmentRequest {

    @NotNull
    private Long airlineId;

    public Long getAirlineId() {
        return airlineId;
    }

    public void setAirlineId(Long airlineId) {
        this.airlineId = airlineId;
    }
}
