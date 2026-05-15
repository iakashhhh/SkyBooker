package com.skybooker.notificationservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SupportInquiryRequest {

    @NotBlank(message = "fullName is required")
    @Size(min = 2, max = 120, message = "fullName length must be between 2 and 120")
    private String fullName;

    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    private String email;

    @Size(max = 40, message = "phone length must be up to 40")
    private String phone;

    @Size(max = 255, message = "bookingId length must be up to 255")
    private String bookingId;

    @NotBlank(message = "category is required")
    @Size(max = 100, message = "category length must be up to 100")
    private String category;

    @NotBlank(message = "subject is required")
    @Size(min = 6, max = 180, message = "subject length must be between 6 and 180")
    private String subject;

    @NotBlank(message = "message is required")
    @Size(min = 20, max = 5000, message = "message length must be between 20 and 5000")
    private String message;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
