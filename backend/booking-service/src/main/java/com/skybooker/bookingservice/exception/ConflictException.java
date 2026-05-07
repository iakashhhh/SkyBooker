package com.skybooker.bookingservice.exception;

/**
 * Raised for hold/payment/availability conflicts.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
