package com.skybooker.flightservice.exception;

/**
 * This exception is thrown when request data violates business validations.
 * It maps to HTTP 400 responses via global exception handling.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
