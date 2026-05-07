package com.skybooker.bookingservice.exception;

/**
 * Raised when booking resources are missing.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
