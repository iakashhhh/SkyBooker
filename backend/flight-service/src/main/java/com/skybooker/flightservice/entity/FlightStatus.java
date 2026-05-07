package com.skybooker.flightservice.entity;

/**
 * This enum defines lifecycle states of a flight operation.
 * It is used in status update flows and search responses.
 */
public enum FlightStatus {
    ON_TIME,
    DELAYED,
    CANCELLED,
    DEPARTED,
    ARRIVED
}
