package com.skybooker.authservice.dto;

public class InternalUserEmailResponse {
    private Long userId;
    private String email;

    public InternalUserEmailResponse() {
    }

    public InternalUserEmailResponse(Long userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
