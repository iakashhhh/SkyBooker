package com.skybooker.flightservice.exception;

/**
 * This exception is thrown when requested flight data is missing.
 * It maps to HTTP 404 responses in a consistent format.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
