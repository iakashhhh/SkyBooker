package com.skybooker.flightservice.entity;

/**
 * This enum lists supported seat classes for fare calculations.
 * Base fare is multiplied by seat class factor during search.
 */
public enum SeatClass {
    ECONOMY,
    PREMIUM_ECONOMY,
    BUSINESS,
    FIRST
}
