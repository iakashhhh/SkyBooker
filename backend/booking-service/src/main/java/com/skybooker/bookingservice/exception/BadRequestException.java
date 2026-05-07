package com.skybooker.bookingservice.exception;

/**
 * Raised for invalid booking payloads and illegal transitions.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
