package com.skybooker.authservice.service;

import com.skybooker.authservice.dto.AuthResponse;
import com.skybooker.authservice.dto.ChangePasswordRequest;
import com.skybooker.authservice.dto.LoginRequest;
import com.skybooker.authservice.dto.ProfileResponse;
import com.skybooker.authservice.dto.RegisterRequest;
import com.skybooker.authservice.dto.UpdateProfileRequest;
import com.skybooker.authservice.entity.User;
import com.skybooker.authservice.entity.UserRole;
import com.skybooker.authservice.exception.BadRequestException;
import com.skybooker.authservice.exception.ResourceNotFoundException;
import com.skybooker.authservice.repository.UserRepository;
import com.skybooker.authservice.security.JwtService;
import com.skybooker.authservice.security.TokenBlacklistService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * This class contains unit tests for AuthService business logic.
 * Mockito is used to isolate dependencies and validate core behavior.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private AuthService authService;

    /**
     * Verifies register returns token and role when data is valid.
     */
    @Test
    void registerShouldCreateUserSuccessfully() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Akash Sharma");
        request.setEmail("akash@test.com");
        request.setPassword("Password@123");
        request.setPhone("9876543210");
        request.setPassportNumber("P1234567");
        request.setNationality("Indian");
        request.setRole(UserRole.PASSENGER);

        User savedUser = new User();
        savedUser.setEmail("akash@test.com");
        savedUser.setRole(UserRole.PASSENGER);

        when(userRepository.existsByEmail("akash@test.com")).thenReturn(false);
        when(userRepository.existsByPassportNumber("P1234567")).thenReturn(false);
        when(passwordEncoder.encode("Password@123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken("akash@test.com", "PASSENGER")).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        Assertions.assertEquals("jwt-token", response.getToken());
        Assertions.assertEquals("akash@test.com", response.getEmail());
        Assertions.assertEquals("PASSENGER", response.getRole());
    }

    /**
     * Verifies register fails when email already exists.
     */
    @Test
    void registerShouldFailWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@test.com");

        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        Assertions.assertThrows(BadRequestException.class, () -> authService.register(request));
    }

    /**
     * Verifies login returns token when credentials are valid.
     */
    @Test
    void loginShouldReturnTokenSuccessfully() {
        LoginRequest request = new LoginRequest();
        request.setEmail("akash@test.com");
        request.setPassword("Password@123");

        User user = new User();
        user.setEmail("akash@test.com");
        user.setRole(UserRole.ADMIN);
        user.setActive(true);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(userRepository.findByEmail("akash@test.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("akash@test.com", "ADMIN")).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        Assertions.assertEquals("jwt-token", response.getToken());
        Assertions.assertEquals("ADMIN", response.getRole());
    }

    /**
     * Verifies profile lookup returns expected fields.
     */
    @Test
    void getProfileShouldReturnUserProfile() {
        User user = new User();
        user.setFullName("Akash Sharma");
        user.setEmail("akash@test.com");
        user.setPhone("9999999999");
        user.setPassportNumber("P1234567");
        user.setNationality("Indian");
        user.setRole(UserRole.AIRLINE_STAFF);
        user.setProvider("LOCAL");
        user.setActive(true);

        when(userRepository.findByEmail("akash@test.com")).thenReturn(Optional.of(user));

        ProfileResponse profileResponse = authService.getProfile("akash@test.com");

        Assertions.assertEquals("Akash Sharma", profileResponse.getFullName());
        Assertions.assertEquals("P1234567", profileResponse.getPassportNumber());
        Assertions.assertEquals("AIRLINE_STAFF", profileResponse.getRole());
    }

    /**
     * Verifies profile lookup fails when user is missing.
     */
    @Test
    void getProfileShouldThrowWhenUserNotFound() {
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class,
                () -> authService.getProfile("missing@test.com"));
    }

    /**
     * Verifies refresh returns a new token from a valid existing token.
     */
    @Test
    void refreshTokenShouldReturnNewToken() {
        User user = new User();
        user.setId(99L);
        user.setEmail("akash@test.com");
        user.setRole(UserRole.ADMIN);

        when(tokenBlacklistService.isBlacklisted("old-token")).thenReturn(false);
        when(jwtService.isTokenValid("old-token")).thenReturn(true);
        when(jwtService.extractEmail("old-token")).thenReturn("akash@test.com");
        when(jwtService.extractRole("old-token")).thenReturn("ADMIN");
        when(userRepository.findByEmail("akash@test.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("akash@test.com", "ADMIN")).thenReturn("new-token");

        AuthResponse response = authService.refreshToken("old-token");

        Assertions.assertEquals("new-token", response.getToken());
        Assertions.assertEquals("ADMIN", response.getRole());
    }

    /**
     * Verifies logout blacklists the token.
     */
    @Test
    void logoutShouldBlacklistToken() {
        when(tokenBlacklistService.isBlacklisted("token")).thenReturn(false);
        when(jwtService.isTokenValid("token")).thenReturn(true);

        authService.logout("token");
    }

    /**
     * Verifies password change updates password hash when current password is valid.
     */
    @Test
    void changePasswordShouldUpdatePassword() {
        User user = new User();
        user.setEmail("akash@test.com");
        user.setPasswordHash("old-hash");

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("Old@1234");
        request.setNewPassword("NewPass@123");

        when(userRepository.findByEmail("akash@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Old@1234", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPass@123")).thenReturn("new-hash");

        authService.changePassword("akash@test.com", request);

        Assertions.assertEquals("new-hash", user.getPasswordHash());
    }

    /**
     * Verifies profile update applies new profile values.
     */
    @Test
    void updateProfileShouldUpdateUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("akash@test.com");
        user.setRole(UserRole.PASSENGER);
        user.setProvider("LOCAL");
        user.setActive(true);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Akash Sharma");
        request.setPhone("9876543210");
        request.setPassportNumber("ABCD1234");
        request.setNationality("Indian");

        when(userRepository.findByEmail("akash@test.com")).thenReturn(Optional.of(user));
        when(userRepository.findByPhone("9876543210")).thenReturn(Optional.empty());
        when(userRepository.findByPassportNumber("ABCD1234")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileResponse response = authService.updateProfile("akash@test.com", request);

        Assertions.assertEquals("9876543210", response.getPhone());
        Assertions.assertEquals("ABCD1234", response.getPassportNumber());
    }
}
