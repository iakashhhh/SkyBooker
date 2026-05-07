package com.skybooker.seatservice.exception;

/**
 * Thrown when request input or lifecycle transitions are invalid.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
