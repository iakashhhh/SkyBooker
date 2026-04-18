package com.skybooker.authservice.dto;

import com.skybooker.authservice.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * This DTO captures all fields required for user registration.
 * Validation annotations keep input checks simple and centralized.
 */
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
        @Pattern(regexp = "^[A-Za-z .'-]{2,80}$", message = "Full name format is invalid")
    private String fullName;

    @Email(message = "Email must be valid")
    @NotBlank(message = "Email is required")
        @Pattern(regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$", message = "Email format is invalid")
    private String email;

        @Size(min = 8, message = "Password must be at least 8 characters")
    @NotBlank(message = "Password is required")
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,20}$",
            message = "Password must be 8-20 chars with upper, lower, digit and special symbol"
        )
    private String password;

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone must be 10 to 15 digits")
        private String phone;

    @NotBlank(message = "Passport number is required")
        @Pattern(regexp = "^[A-Z0-9]{6,12}$", message = "Passport number format is invalid")
    private String passportNumber;

    @NotBlank(message = "Nationality is required")
        @Pattern(regexp = "^[A-Za-z ]{2,60}$", message = "Nationality format is invalid")
    private String nationality;

    @NotNull(message = "Role is required")
    private UserRole role;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }
}
