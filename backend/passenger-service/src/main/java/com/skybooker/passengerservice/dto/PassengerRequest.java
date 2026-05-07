package com.skybooker.passengerservice.dto;

import com.skybooker.passengerservice.entity.PassengerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class PassengerRequest {

    @NotBlank
    private String bookingId;

    @NotBlank
    private String title;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotNull
    private LocalDate dateOfBirth;

    @NotBlank
    private String gender;

    @NotNull
    private PassengerType passengerType;

    @NotBlank
    private String passportNumber;

    @NotBlank
    private String nationality;

    @NotNull
    private LocalDate passportExpiry;

    private String mealPreference;

    private Integer extraBaggageKg;
}
