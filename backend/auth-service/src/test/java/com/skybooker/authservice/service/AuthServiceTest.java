package com.skybooker.authservice.service;

import com.skybooker.authservice.dto.AuthResponse;
import com.skybooker.authservice.dto.ChangePasswordRequest;
import com.skybooker.authservice.dto.ForgotPasswordRequest;
import com.skybooker.authservice.dto.GoogleLoginRequest;
import com.skybooker.authservice.dto.LoginRequest;
import com.skybooker.authservice.dto.ProfileResponse;
import com.skybooker.authservice.dto.RegisterRequest;
import com.skybooker.authservice.dto.ResetPasswordRequest;
import com.skybooker.authservice.dto.UpdateProfileRequest;
import com.skybooker.authservice.entity.PasswordResetToken;
import com.skybooker.authservice.entity.User;
import com.skybooker.authservice.entity.UserRole;
import com.skybooker.authservice.exception.BadRequestException;
import com.skybooker.authservice.exception.ResourceNotFoundException;
import com.skybooker.authservice.repository.PasswordResetTokenRepository;
import com.skybooker.authservice.repository.UserRepository;
import com.skybooker.authservice.security.JwtService;
import com.skybooker.authservice.security.TokenBlacklistService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import jakarta.mail.internet.MimeMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

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

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AuthService authService;

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
        savedUser.setId(1L);
        savedUser.setEmail("akash@test.com");
        savedUser.setRole(UserRole.PASSENGER);

        when(userRepository.findByEmailIgnoreCase("akash@test.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("9876543210")).thenReturn(Optional.empty());
        when(userRepository.findByPassportNumber("P1234567")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password@123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken("akash@test.com", "PASSENGER")).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        Assertions.assertEquals("jwt-token", response.getToken());
        Assertions.assertEquals("akash@test.com", response.getEmail());
        Assertions.assertEquals("PASSENGER", response.getRole());
    }

    @Test
    void registerShouldFailWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@test.com");
        request.setRole(UserRole.PASSENGER);

        User existing = new User();
        existing.setId(10L);
        existing.setEmail("existing@test.com");
        existing.setActive(true);
        when(userRepository.findByEmailIgnoreCase("existing@test.com")).thenReturn(Optional.of(existing));

        Assertions.assertThrows(BadRequestException.class, () -> authService.register(request));
    }

    @Test
    void registerShouldReactivateDeactivatedUserWithRequestedRole() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Admin User");
        request.setEmail("admin@test.com");
        request.setPassword("Password@123");
        request.setPhone("9898989898");
        request.setPassportNumber("A1234567");
        request.setNationality("Indian");
        request.setRole(UserRole.ADMIN);

        User existing = new User();
        existing.setId(99L);
        existing.setEmail("admin@test.com");
        existing.setActive(false);
        existing.setRole(UserRole.PASSENGER);
        existing.setPhone("9898989898");
        existing.setPassportNumber("A1234567");

        when(userRepository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(existing));
        when(userRepository.findByPhone("9898989898")).thenReturn(Optional.of(existing));
        when(userRepository.findByPassportNumber("A1234567")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("Password@123")).thenReturn("encoded-password");
        when(userRepository.save(existing)).thenReturn(existing);
        when(jwtService.generateToken("admin@test.com", "ADMIN")).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        Assertions.assertEquals("ADMIN", response.getRole());
        Assertions.assertTrue(existing.isActive());
        Assertions.assertEquals(UserRole.ADMIN, existing.getRole());
        Assertions.assertEquals("encoded-password", existing.getPasswordHash());
    }

    @Test
    void loginShouldReturnTokenSuccessfully() {
        LoginRequest request = new LoginRequest();
        request.setEmail("akash@test.com");
        request.setPassword("Password@123");

        User user = new User();
        user.setId(7L);
        user.setEmail("akash@test.com");
        user.setRole(UserRole.ADMIN);
        user.setActive(true);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(userRepository.findByEmailIgnoreCase("akash@test.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("akash@test.com", "ADMIN")).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        Assertions.assertEquals("jwt-token", response.getToken());
        Assertions.assertEquals("ADMIN", response.getRole());
        Assertions.assertEquals(7L, response.getUserId());
    }

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

        when(userRepository.findByEmailIgnoreCase("akash@test.com")).thenReturn(Optional.of(user));

        ProfileResponse profileResponse = authService.getProfile("akash@test.com");

        Assertions.assertEquals("Akash Sharma", profileResponse.getFullName());
        Assertions.assertEquals("P1234567", profileResponse.getPassportNumber());
        Assertions.assertEquals("AIRLINE_STAFF", profileResponse.getRole());
    }

    @Test
    void getProfileShouldThrowWhenUserNotFound() {
        when(userRepository.findByEmailIgnoreCase("missing@test.com")).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class,
            () -> authService.getProfile("missing@test.com"));
    }

    @Test
    void refreshTokenShouldReturnNewToken() {
        User user = new User();
        user.setId(5L);
        user.setEmail("akash@test.com");
        user.setRole(UserRole.ADMIN);
        user.setActive(true);

        when(tokenBlacklistService.isBlacklisted("old-token")).thenReturn(false);
        when(jwtService.isTokenValid("old-token")).thenReturn(true);
        when(jwtService.extractEmail("old-token")).thenReturn("akash@test.com");
        when(jwtService.extractRole("old-token")).thenReturn("ADMIN");
        when(userRepository.findByEmailIgnoreCase("akash@test.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("akash@test.com", "ADMIN")).thenReturn("new-token");

        AuthResponse response = authService.refreshToken("old-token");

        Assertions.assertEquals("new-token", response.getToken());
        Assertions.assertEquals("ADMIN", response.getRole());
        Assertions.assertEquals(5L, response.getUserId());
    }

    @Test
    void logoutShouldBlacklistToken() {
        when(tokenBlacklistService.isBlacklisted("token")).thenReturn(false);
        when(jwtService.isTokenValid("token")).thenReturn(true);

        authService.logout("token");

        verify(tokenBlacklistService).blacklistToken("token");
    }

    @Test
    void changePasswordShouldUpdatePassword() {
        User user = new User();
        user.setEmail("akash@test.com");
        user.setPasswordHash("old-hash");

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("Old@1234");
        request.setNewPassword("NewPass@123");

        when(userRepository.findByEmailIgnoreCase("akash@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Old@1234", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPass@123")).thenReturn("new-hash");

        authService.changePassword("akash@test.com", request);

        Assertions.assertEquals("new-hash", user.getPasswordHash());
    }

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

        when(userRepository.findByEmailIgnoreCase("akash@test.com")).thenReturn(Optional.of(user));
        when(userRepository.findByPhone("9876543210")).thenReturn(Optional.empty());
        when(userRepository.findByPassportNumber("ABCD1234")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileResponse response = authService.updateProfile("akash@test.com", request);

        Assertions.assertEquals("9876543210", response.getPhone());
        Assertions.assertEquals("ABCD1234", response.getPassportNumber());
    }

    @Test
    void loginShouldFailWhenUserIsDeactivated() {
        LoginRequest request = new LoginRequest();
        request.setEmail("akash@test.com");
        request.setPassword("Password@123");

        User user = new User();
        user.setEmail("akash@test.com");
        user.setRole(UserRole.PASSENGER);
        user.setActive(false);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(userRepository.findByEmailIgnoreCase("akash@test.com")).thenReturn(Optional.of(user));

        Assertions.assertThrows(BadRequestException.class, () -> authService.login(request));
    }

    @Test
    void refreshTokenShouldFailWhenTokenIsBlacklisted() {
        when(tokenBlacklistService.isBlacklisted("old-token")).thenReturn(true);

        Assertions.assertThrows(BadRequestException.class, () -> authService.refreshToken("old-token"));
    }

    @Test
    void updateProfileShouldFailWhenPhoneBelongsToAnotherUser() {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setEmail("akash@test.com");

        User anotherUser = new User();
        anotherUser.setId(2L);
        anotherUser.setPhone("9876543210");

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Akash Sharma");
        request.setPhone("9876543210");
        request.setPassportNumber("ABCD1234");
        request.setNationality("Indian");

        when(userRepository.findByEmailIgnoreCase("akash@test.com")).thenReturn(Optional.of(currentUser));
        when(userRepository.findByPhone("9876543210")).thenReturn(Optional.of(anotherUser));

        Assertions.assertThrows(BadRequestException.class, () -> authService.updateProfile("akash@test.com", request));
    }

    @Test
    void deactivateAccountShouldMarkUserInactive() {
        User user = new User();
        user.setEmail("akash@test.com");
        user.setActive(true);

        when(userRepository.findByEmailIgnoreCase("akash@test.com")).thenReturn(Optional.of(user));

        authService.deactivateAccount("akash@test.com");

        Assertions.assertFalse(user.isActive());
        verify(userRepository).save(user);
    }

    @Test
    void resetPasswordShouldFailWhenOtpIsInvalid() {
        User user = new User();
        user.setEmail("akash@test.com");
        user.setPasswordHash("old-hash");

        PasswordResetToken token = new PasswordResetToken();
        token.setEmail("akash@test.com");
        token.setOtpCode("111111");
        token.setUsed(false);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("akash@test.com");
        request.setOtpCode("999999");
        request.setNewPassword("New@Password1");

        when(userRepository.findByEmailIgnoreCase("akash@test.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("akash@test.com"))
            .thenReturn(Optional.of(token));

        Assertions.assertThrows(BadRequestException.class, () -> authService.resetPassword(request));
    }

    @Test
    void loginShouldFailWhenUserNotFound() {
        LoginRequest request = new LoginRequest();
        request.setEmail("missing@test.com");
        request.setPassword("Password@123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(userRepository.findByEmailIgnoreCase("missing@test.com")).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class, () -> authService.login(request));
    }

    @Test
    void refreshTokenShouldFailWhenTokenInvalid() {
        when(tokenBlacklistService.isBlacklisted("bad-token")).thenReturn(false);
        when(jwtService.isTokenValid("bad-token")).thenReturn(false);

        Assertions.assertThrows(BadRequestException.class, () -> authService.refreshToken("bad-token"));
    }

    @Test
    void changePasswordShouldFailWhenCurrentPasswordMismatch() {
        User user = new User();
        user.setEmail("akash@test.com");
        user.setPasswordHash("old-hash");

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("Wrong@123");
        request.setNewPassword("NewPass@123");

        when(userRepository.findByEmailIgnoreCase("akash@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Wrong@123", "old-hash")).thenReturn(false);

        Assertions.assertThrows(BadRequestException.class, () -> authService.changePassword("akash@test.com", request));
    }

    @Test
    void resetPasswordShouldFailWhenOtpExpired() {
        User user = new User();
        user.setEmail("akash@test.com");
        user.setPasswordHash("old-hash");

        PasswordResetToken token = new PasswordResetToken();
        token.setEmail("akash@test.com");
        token.setOtpCode("111111");
        token.setUsed(false);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("akash@test.com");
        request.setOtpCode("111111");
        request.setNewPassword("New@Password1");

        when(userRepository.findByEmailIgnoreCase("akash@test.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("akash@test.com"))
            .thenReturn(Optional.of(token));

        Assertions.assertThrows(BadRequestException.class, () -> authService.resetPassword(request));
    }

    @Test
    void sendForgotPasswordOtpShouldDoNothingWhenUserNotFound() {
        when(userRepository.findByEmailIgnoreCase("nobody@test.com")).thenReturn(Optional.empty());

        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("nobody@test.com");
        authService.sendForgotPasswordOtp(request);

        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
    }

    @Test
    void sendForgotPasswordOtpShouldCreateTokenAndSendMailWhenUserExists() {
        User user = new User();
        user.setEmail("akash@test.com");
        user.setFullName("Akash Sharma");
        when(userRepository.findByEmailIgnoreCase("akash@test.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findByEmailAndUsedFalse("akash@test.com")).thenReturn(List.of());
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));

        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("akash@test.com");
        authService.sendForgotPasswordOtp(request);

        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void loginWithGoogleShouldCreateUserWhenFirstTimeGoogleLogin() {
        ReflectionTestUtils.setField(authService, "googleClientId", "google-client-1");

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/tokeninfo?id_token=token-123"),
            eq(HttpMethod.GET),
            eq(HttpEntity.EMPTY),
            any(org.springframework.core.ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
            "aud", "google-client-1",
            "email", "google@test.com",
            "email_verified", "true",
            "name", "Google User",
            "picture", "http://img"
        )));

        when(userRepository.findByEmailIgnoreCase("google@test.com")).thenReturn(Optional.empty());
        when(userRepository.existsByPhone(any())).thenReturn(false);
        when(userRepository.existsByPassportNumber(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(55L);
            return saved;
        });
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(jwtService.generateToken("google@test.com", "PASSENGER")).thenReturn("jwt-google");

        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("token-123");
        request.setRole(UserRole.PASSENGER);
        AuthResponse response = authService.loginWithGoogle(request);

        Assertions.assertEquals("jwt-google", response.getToken());
        Assertions.assertEquals("PASSENGER", response.getRole());
    }

    @Test
    void loginWithGoogleShouldFailWhenAudienceDoesNotMatchConfiguredClient() {
        ReflectionTestUtils.setField(authService, "googleClientId", "expected-client");

        when(restTemplate.exchange(
            eq("https://oauth2.googleapis.com/tokeninfo?id_token=token-bad"),
            eq(HttpMethod.GET),
            eq(HttpEntity.EMPTY),
            any(org.springframework.core.ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
            "aud", "other-client",
            "email", "google@test.com",
            "email_verified", "true"
        )));

        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("token-bad");
        request.setRole(UserRole.PASSENGER);

        Assertions.assertThrows(BadRequestException.class, () -> authService.loginWithGoogle(request));
    }

    @Test
    void registerShouldFailForAirlineStaffWhenAirlineIdMissing() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Staff User");
        request.setEmail("staff@test.com");
        request.setPassword("Password@123");
        request.setPhone("9000000010");
        request.setPassportNumber("S1234567");
        request.setNationality("Indian");
        request.setRole(UserRole.AIRLINE_STAFF);
        request.setAirlineId(null);

        when(userRepository.findByEmailIgnoreCase("staff@test.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("9000000010")).thenReturn(Optional.empty());
        when(userRepository.findByPassportNumber("S1234567")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password@123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(81L);
            return saved;
        });

        Assertions.assertThrows(BadRequestException.class, () -> authService.register(request));
    }

    @Test
    void getAllUsersShouldMapProfiles() {
        User user = new User();
        user.setId(3L);
        user.setFullName("User One");
        user.setEmail("user1@test.com");
        user.setPhone("9999999999");
        user.setPassportNumber("P0000001");
        user.setNationality("Indian");
        user.setRole(UserRole.PASSENGER);
        user.setProvider("LOCAL");
        user.setActive(true);

        when(userRepository.findAll()).thenReturn(List.of(user));

        List<ProfileResponse> profiles = authService.getAllUsers();

        Assertions.assertEquals(1, profiles.size());
        Assertions.assertEquals("user1@test.com", profiles.get(0).getEmail());
    }
}
