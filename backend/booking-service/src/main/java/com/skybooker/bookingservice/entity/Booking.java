package com.skybooker.bookingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Core booking record persisted for each user booking request.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @Column(name = "booking_id", nullable = false, length = 36)
    private String bookingId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long flightId;

    @Column(name = "selected_seat_ids", nullable = false, length = 512)
    private String selectedSeatIds;

    @Column(nullable = false, unique = true, length = 6)
    private String pnrCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripType tripType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalFare;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "seat_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal seatCharge;

    @Column(name = "baggage_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal baggageCharge;

    @Column(name = "meal_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal mealCharge;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxes;

    @Column(length = 40)
    private String mealPreference;

    @Column(nullable = false)
    private Integer luggageKg;

    @Column(nullable = false, length = 120)
    private String contactEmail;

    @Column(nullable = false, length = 25)
    private String contactPhone;

    @Column(nullable = false)
    private LocalDateTime bookedAt;

    @Column(length = 64)
    private String paymentId;

    @PrePersist
    void prePersist() {
        if (this.bookingId == null || this.bookingId.isBlank()) {
            this.bookingId = UUID.randomUUID().toString();
        }
        if (this.bookedAt == null) {
            this.bookedAt = LocalDateTime.now();
        }
        if (this.totalFare == null) {
            this.totalFare = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (this.baseFare == null) {
            this.baseFare = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (this.seatCharge == null) {
            this.seatCharge = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (this.baggageCharge == null) {
            this.baggageCharge = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (this.mealCharge == null) {
            this.mealCharge = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (this.taxes == null) {
            this.taxes = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (this.luggageKg == null) {
            this.luggageKg = 0;
        }
    }
}
