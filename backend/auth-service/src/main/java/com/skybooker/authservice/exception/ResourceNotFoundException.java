package com.skybooker.authservice.exception;

/**
 * This exception is thrown when required data does not exist in storage.
 * Example: asking profile for a user that cannot be found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
