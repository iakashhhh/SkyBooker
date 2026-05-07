package com.skybooker.seatservice.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stores seat inventory and lifecycle status per flight.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "seats",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_seat_flight_number", columnNames = {"flight_id", "seat_number"})
    }
)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seatId;

    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    @Column(name = "seat_number", nullable = false, length = 8)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_class", nullable = false, length = 20)
    private SeatClass seatClass;

    @Column(name = "seat_row", nullable = false)
    private Integer row;

    @Column(name = "seat_column", nullable = false, length = 2)
    private String column;

    @Column(name = "is_window", nullable = false)
    private boolean isWindow;

    @Column(name = "is_aisle", nullable = false)
    private boolean isAisle;

    @Column(name = "has_extra_legroom", nullable = false)
    private boolean hasExtraLegroom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    @Column(name = "price_multiplier", nullable = false, precision = 8, scale = 2)
    private BigDecimal priceMultiplier;

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @Version
    private Long version;
}
