package com.skybooker.authservice.dto;

import java.time.LocalDate;

/**
 * This DTO returns user profile information for authenticated users.
 * Sensitive values like password are intentionally not included.
 */
public class ProfileResponse {

    private Long userId;
    private String fullName;
    private String email;
    private String phone;
    private String passportNumber;
    private String nationality;
    private LocalDate dateOfBirth;
    private String profilePhotoUrl;
    private String role;
    private String provider;
    private boolean active;

    public ProfileResponse(Long userId,
                           String fullName,
                           String email,
                           String phone,
                           String passportNumber,
                           String nationality,
                           LocalDate dateOfBirth,
                           String profilePhotoUrl,
                           String role,
                           String provider,
                           boolean active) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.passportNumber = passportNumber;
        this.nationality = nationality;
        this.dateOfBirth = dateOfBirth;
        this.profilePhotoUrl = profilePhotoUrl;
        this.role = role;
        this.provider = provider;
        this.active = active;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

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

    public String getPassportNumber() {
        return passportNumber;
    }

    public void setPassportNumber(String passportNumber) {
        this.passportNumber = passportNumber;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public void setProfilePhotoUrl(String profilePhotoUrl) {
        this.profilePhotoUrl = profilePhotoUrl;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
