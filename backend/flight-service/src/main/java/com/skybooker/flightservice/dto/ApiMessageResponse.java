package com.skybooker.flightservice.dto;

/**
 * This DTO provides a simple message response for write operations.
 * It keeps delete/update feedback readable for clients.
 */
public class ApiMessageResponse {

    private String message;

    public ApiMessageResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
