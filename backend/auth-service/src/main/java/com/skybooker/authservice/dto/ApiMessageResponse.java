package com.skybooker.authservice.dto;

/**
 * This DTO provides a simple message-only API response format.
 * It is used for logout, password update, and deactivate operations.
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
