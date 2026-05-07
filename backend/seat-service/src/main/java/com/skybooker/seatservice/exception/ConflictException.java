package com.skybooker.seatservice.exception;

/**
 * Thrown when optimistic lock or state conflict occurs.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
