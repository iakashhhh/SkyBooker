package com.skybooker.seatservice.entity;

/**
 * Seat lifecycle states used by hold and confirmation flow.
 */
public enum SeatStatus {
    AVAILABLE,
    HELD,
    CONFIRMED,
    BLOCKED
}
