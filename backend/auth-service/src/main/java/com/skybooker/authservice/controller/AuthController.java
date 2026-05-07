package com.skybooker.authservice.controller;

import com.skybooker.authservice.dto.AuthResponse;
import com.skybooker.authservice.dto.ChangePasswordRequest;
import com.skybooker.authservice.dto.ForgotPasswordRequest;
import com.skybooker.authservice.dto.GoogleLoginRequest;
import com.skybooker.authservice.dto.InternalUserEmailResponse;
import com.skybooker.authservice.dto.LoginRequest;
import com.skybooker.authservice.dto.ProfileResponse;
import com.skybooker.authservice.dto.RegisterRequest;
import com.skybooker.authservice.dto.ResetPasswordRequest;
import com.skybooker.authservice.dto.UpdateProfileRequest;
import com.skybooker.authservice.dto.ApiMessageResponse;
import com.skybooker.authservice.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * This controller exposes all required auth APIs.
 * It delegates business logic to service layer for clean structure.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user and returns JWT token.
     */
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * Authenticates existing user and returns JWT token.
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/google")
    public AuthResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request);
    }

    @PostMapping("/password/forgot")
    public ApiMessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.sendForgotPasswordOtp(request);
        return new ApiMessageResponse("If the email exists, a reset OTP has been sent.");
    }

    @PostMapping("/password/reset")
    public ApiMessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return new ApiMessageResponse("Password reset successfully");
    }

    /**
     * Refreshes JWT token using currently valid bearer token.
     */
    @PostMapping("/refresh")
    public AuthResponse refreshToken(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        return authService.refreshToken(extractBearerToken(authorizationHeader));
    }

    /**
     * Logs out user by blacklisting provided bearer token.
     */
    @PostMapping("/logout")
    public ApiMessageResponse logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        authService.logout(extractBearerToken(authorizationHeader));
        return new ApiMessageResponse("Logged out successfully");
    }

    /**
     * Returns current authenticated user profile details.
     */
    @GetMapping("/profile")
    public ProfileResponse getProfile(Authentication authentication) {
        return authService.getProfile(authentication.getName());
    }

    /**
     * Updates profile fields for current authenticated user.
     */
    @PutMapping("/profile")
    public ProfileResponse updateProfile(Authentication authentication,
                                         @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(authentication.getName(), request);
    }

    /**
     * Changes account password for current authenticated user.
     */
    @PutMapping("/password")
    public ApiMessageResponse changePassword(Authentication authentication,
                                             @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authentication.getName(), request);
        return new ApiMessageResponse("Password changed successfully");
    }

    /**
     * Deactivates current authenticated user account.
     */
    @PutMapping("/deactivate")
    public ApiMessageResponse deactivateAccount(Authentication authentication,
                                                @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        authService.deactivateAccount(authentication.getName());
        authService.logout(extractBearerToken(authorizationHeader));
        return new ApiMessageResponse("Account deactivated successfully");
    }

    /**
     * Returns all users for admin panel access.
     */
    @GetMapping("/users")
    public List<ProfileResponse> getAllUsers() {
        return authService.getAllUsers();
    }

    @GetMapping("/internal/users/{userId}/email")
    public InternalUserEmailResponse getUserEmailById(@PathVariable Long userId) {
        return new InternalUserEmailResponse(userId, authService.getUserEmailById(userId));
    }

    /**
     * Extracts JWT value from Authorization header.
     */
    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return "";
        }
        return authorizationHeader.substring(7);
    }
}
