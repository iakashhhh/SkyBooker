package com.skybooker.authservice.controller;

import com.skybooker.authservice.dto.ApiMessageResponse;
import com.skybooker.authservice.dto.AuthResponse;
import com.skybooker.authservice.dto.ChangePasswordRequest;
import com.skybooker.authservice.dto.ForgotPasswordRequest;
import com.skybooker.authservice.dto.GoogleLoginRequest;
import com.skybooker.authservice.dto.LoginRequest;
import com.skybooker.authservice.dto.ProfileResponse;
import com.skybooker.authservice.dto.RegisterRequest;
import com.skybooker.authservice.dto.ResetPasswordRequest;
import com.skybooker.authservice.dto.UpdateProfileRequest;
import com.skybooker.authservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService);
    }

    @Test
    void shouldDelegateRegisterAndLoginEndpoints() {
        RegisterRequest registerRequest = new RegisterRequest();
        LoginRequest loginRequest = new LoginRequest();
        GoogleLoginRequest googleLoginRequest = new GoogleLoginRequest();
        AuthResponse registerResponse = new AuthResponse("register-token", "register@skybooker.com", "PASSENGER", 1L);
        AuthResponse loginResponse = new AuthResponse("login-token", "login@skybooker.com", "PASSENGER", 2L);
        AuthResponse googleResponse = new AuthResponse("google-token", "google@skybooker.com", "PASSENGER", 3L);

        when(authService.register(registerRequest)).thenReturn(registerResponse);
        when(authService.login(loginRequest)).thenReturn(loginResponse);
        when(authService.loginWithGoogle(googleLoginRequest)).thenReturn(googleResponse);

        assertEquals("register-token", controller.register(registerRequest).getToken());
        assertEquals("login-token", controller.login(loginRequest).getToken());
        assertEquals("google-token", controller.loginWithGoogle(googleLoginRequest).getToken());
    }

    @Test
    void shouldHandleForgotAndResetPasswordMessages() {
        ForgotPasswordRequest forgotPasswordRequest = new ForgotPasswordRequest();
        ResetPasswordRequest resetPasswordRequest = new ResetPasswordRequest();

        ApiMessageResponse forgotResponse = controller.forgotPassword(forgotPasswordRequest);
        ApiMessageResponse resetResponse = controller.resetPassword(resetPasswordRequest);

        verify(authService).sendForgotPasswordOtp(forgotPasswordRequest);
        verify(authService).resetPassword(resetPasswordRequest);
        assertEquals("If the email exists, a reset OTP has been sent.", forgotResponse.getMessage());
        assertEquals("Password reset successfully", resetResponse.getMessage());
    }

    @Test
    void shouldExtractBearerTokenForRefreshLogoutAndDeactivate() {
        AuthResponse refreshResponse = new AuthResponse("new-token", "user@skybooker.com", "PASSENGER", 9L);
        when(authService.refreshToken("abc123")).thenReturn(refreshResponse);

        Authentication authentication = new UsernamePasswordAuthenticationToken("user@skybooker.com", "x");
        ApiMessageResponse logoutResponse = controller.logout("Bearer abc123");
        ApiMessageResponse deactivateResponse = controller.deactivateAccount(authentication, "Bearer abc123");

        assertEquals("new-token", controller.refreshToken("Bearer abc123").getToken());
        assertEquals("Logged out successfully", logoutResponse.getMessage());
        assertEquals("Account deactivated successfully", deactivateResponse.getMessage());
        verify(authService).refreshToken("abc123");
        verify(authService, times(2)).logout("abc123");
        verify(authService).deactivateAccount("user@skybooker.com");
    }

    @Test
    void shouldPassEmptyTokenWhenHeaderIsMissingBearerPrefix() {
        controller.refreshToken("Token abc123");
        controller.logout(null);

        verify(authService).refreshToken("");
        verify(authService).logout("");
    }

    @Test
    void shouldDelegateProfilePasswordAndUsersEndpoints() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("member@skybooker.com", "x");
        ProfileResponse profileResponse = profile("member@skybooker.com");
        ProfileResponse updatedResponse = profile("member@skybooker.com");
        UpdateProfileRequest updateProfileRequest = new UpdateProfileRequest();
        ChangePasswordRequest changePasswordRequest = new ChangePasswordRequest();
        when(authService.getProfile("member@skybooker.com")).thenReturn(profileResponse);
        when(authService.updateProfile("member@skybooker.com", updateProfileRequest)).thenReturn(updatedResponse);
        when(authService.getAllUsers()).thenReturn(List.of(profileResponse));
        when(authService.getUserEmailById(42L)).thenReturn("user42@gmail.com");

        assertEquals("member@skybooker.com", controller.getProfile(authentication).getEmail());
        assertEquals("member@skybooker.com", controller.updateProfile(authentication, updateProfileRequest).getEmail());
        assertEquals("Password changed successfully", controller.changePassword(authentication, changePasswordRequest).getMessage());
        assertEquals(1, controller.getAllUsers().size());
        assertEquals("user42@gmail.com", controller.getUserEmailById(42L).getEmail());
        verify(authService).changePassword("member@skybooker.com", changePasswordRequest);
    }

    private ProfileResponse profile(String email) {
        return new ProfileResponse(
            11L,
            "SkyBooker Member",
            email,
            "9999999999",
            "P123456",
            "Indian",
            LocalDate.of(1990, 1, 1),
            null,
            "PASSENGER",
            "LOCAL",
            true
        );
    }
}
