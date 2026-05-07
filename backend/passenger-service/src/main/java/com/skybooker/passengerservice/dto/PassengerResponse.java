package com.skybooker.passengerservice.dto;

import com.skybooker.passengerservice.entity.PassengerType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class PassengerResponse {
    private Long passengerId;
    private String bookingId;
    private String title;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String gender;
    private PassengerType passengerType;
    private String passportNumber;
    private String nationality;
    private LocalDate passportExpiry;
    private String mealPreference;
    private Integer extraBaggageKg;
    private Long seatId;
    private String seatNumber;
    private String ticketNumber;
    private LocalDateTime createdAt;
}
