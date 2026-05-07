package com.skybooker.seatservice.exception;

/**
 * Thrown when seat resources are missing for a flight.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
