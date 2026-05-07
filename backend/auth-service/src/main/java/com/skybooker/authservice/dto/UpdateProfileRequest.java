package com.skybooker.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/**
 * This DTO is used to update profile details of authenticated users.
 * Regex validations are added to enforce clean and predictable data.
 */
public class UpdateProfileRequest {

    @NotBlank(message = "Full name is required")
    @Pattern(regexp = "^[A-Za-z .'-]{2,80}$", message = "Full name format is invalid")
    private String fullName;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone must be 10 to 15 digits")
    private String phone;

    @NotBlank(message = "Passport number is required")
    @Pattern(regexp = "^[A-Z0-9]{6,12}$", message = "Passport number format is invalid")
    private String passportNumber;

    @NotBlank(message = "Nationality is required")
    @Pattern(regexp = "^[A-Za-z ]{2,60}$", message = "Nationality format is invalid")
    private String nationality;

    private LocalDate dateOfBirth;

    private String profilePhotoUrl;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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
}
