package com.skybooker.authservice.exception;

/**
 * This exception is used when request data violates business rules.
 * Example: duplicate email or duplicate passport number.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
