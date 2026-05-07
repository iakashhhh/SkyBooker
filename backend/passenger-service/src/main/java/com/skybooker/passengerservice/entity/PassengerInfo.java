package com.skybooker.passengerservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "passengers")
public class PassengerInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long passengerId;

    @Column(nullable = false)
    private String bookingId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private String gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PassengerType passengerType;

    @Column(nullable = false, unique = true)
    private String passportNumber;

    @Column(nullable = false)
    private String nationality;

    @Column(nullable = false)
    private LocalDate passportExpiry;

    private String mealPreference;

    private Integer extraBaggageKg;

    private Long seatId;

    private String seatNumber;

    @Column(unique = true)
    private String ticketNumber;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
