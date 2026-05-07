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
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * This service contains main authentication business logic.
 * It handles register, login, profile lookup, and token generation.
 */
@Service
public class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;
    private final Random random = new Random();

    @Value("${auth.upstream.airline-airport-base-url:http://localhost:8080}")
    private String airlineAirportBaseUrl;

    @Value("${auth.google.client-id:}")
    private String googleClientId;

    @Value("${auth.password-reset.expiry-minutes:15}")
    private long passwordResetExpiryMinutes;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       TokenBlacklistService tokenBlacklistService,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       JavaMailSender mailSender,
                       RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.mailSender = mailSender;
        this.restTemplate = restTemplate;
    }

    /**
     * Registers new user after duplicate checks and password hashing.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
            .map(existing -> reactivateOrReject(existing, request, normalizedEmail))
            .orElseGet(() -> buildNewUser(request, normalizedEmail));

        User savedUser = userRepository.save(user);
        assignStaffAirlineIfApplicable(savedUser, request.getRole(), request.getAirlineId());
        LOGGER.info("User registered successfully with email: {}", savedUser.getEmail());

        String token = jwtService.generateToken(savedUser.getEmail(), savedUser.getRole().name());
        return new AuthResponse(token, savedUser.getEmail(), savedUser.getRole().name(), savedUser.getId());
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        Map<String, Object> tokenPayload = validateGoogleToken(request.getIdToken());
        String email = normalizeEmail(String.valueOf(tokenPayload.getOrDefault("email", "")));
        if (email.isBlank()) {
            throw new BadRequestException("Google account email is unavailable");
        }

        UserRole requestedRole = request.getRole() == null ? UserRole.PASSENGER : request.getRole();
        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseGet(() -> buildGoogleUser(tokenPayload, email, requestedRole));

        if (!user.isActive()) {
            user.setActive(true);
        }
        if (user.getRole() == null) {
            user.setRole(requestedRole);
        }

        User savedUser = userRepository.save(user);
        assignStaffAirlineIfApplicable(savedUser, savedUser.getRole(), request.getAirlineId());

        String token = jwtService.generateToken(savedUser.getEmail(), savedUser.getRole().name());
        return new AuthResponse(token, savedUser.getEmail(), savedUser.getRole().name(), savedUser.getId());
    }

    private User buildNewUser(RegisterRequest request, String normalizedEmail) {
        assertRegistrationUniqueness(request, null);

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setPassportNumber(request.getPassportNumber());
        user.setNationality(request.getNationality());
        user.setRole(request.getRole() == null ? UserRole.PASSENGER : request.getRole());
        user.setProvider("LOCAL");
        user.setDateOfBirth(request.getDateOfBirth());
        user.setProfilePhotoUrl(request.getProfilePhotoUrl());
        user.setActive(true);
        return user;
    }

    private User reactivateOrReject(User existingUser, RegisterRequest request, String normalizedEmail) {
        if (existingUser.isActive()) {
            throw new BadRequestException("Email is already registered");
        }

        assertRegistrationUniqueness(request, existingUser.getId());
        existingUser.setFullName(request.getFullName());
        existingUser.setEmail(normalizedEmail);
        existingUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        existingUser.setPhone(request.getPhone());
        existingUser.setPassportNumber(request.getPassportNumber());
        existingUser.setNationality(request.getNationality());
        existingUser.setRole(request.getRole() == null ? UserRole.PASSENGER : request.getRole());
        existingUser.setProvider("LOCAL");
        existingUser.setDateOfBirth(request.getDateOfBirth());
        existingUser.setProfilePhotoUrl(request.getProfilePhotoUrl());
        existingUser.setActive(true);
        return existingUser;
    }

    private void assertRegistrationUniqueness(RegisterRequest request, Long currentUserId) {
        userRepository.findByPhone(request.getPhone())
            .filter(existing -> !existing.getId().equals(currentUserId))
            .ifPresent(existing -> {
                throw new BadRequestException("Phone is already registered");
            });

        userRepository.findByPassportNumber(request.getPassportNumber())
            .filter(existing -> !existing.getId().equals(currentUserId))
            .ifPresent(existing -> {
                throw new BadRequestException("Passport number is already registered");
            });
    }

    /**
     * Authenticates user credentials and returns a fresh JWT token.
     */
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
        );

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + normalizedEmail));

        if (!user.isActive()) {
            throw new BadRequestException("Account is deactivated");
        }

        LOGGER.info("User logged in successfully with email: {}", user.getEmail());
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user.getEmail(), user.getRole().name(), user.getId());
    }

    @Transactional
    public void sendForgotPasswordOtp(ForgotPasswordRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(this::createAndSendOtp);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new BadRequestException("Invalid reset request"));

        PasswordResetToken token = passwordResetTokenRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc(normalizedEmail)
            .orElseThrow(() -> new BadRequestException("Invalid reset request"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("OTP expired. Please request a new one");
        }
        if (!token.getOtpCode().equals(request.getOtpCode())) {
            throw new BadRequestException("Invalid OTP");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);
    }

    /**
     * Refreshes JWT token if provided token is valid and not blacklisted.
     */
    public AuthResponse refreshToken(String token) {
        validateIncomingToken(token);
        String email = jwtService.extractEmail(token);
        String role = jwtService.extractRole(token);
        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        String refreshedToken = jwtService.generateToken(email, role);
        return new AuthResponse(refreshedToken, email, role, user.getId());
    }

    /**
     * Logs out user by blacklisting the currently used token.
     */
    public void logout(String token) {
        validateIncomingToken(token);
        tokenBlacklistService.blacklistToken(token);
    }

    /**
     * Returns profile details for authenticated user by email.
     */
    public ProfileResponse getProfile(String email) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        return toProfileResponse(user);
    }

    /**
     * Updates profile fields for currently authenticated user.
     */
    public ProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        userRepository.findByPhone(request.getPhone())
            .filter(existing -> !existing.getId().equals(user.getId()))
            .ifPresent(existing -> {
                throw new BadRequestException("Phone is already registered");
            });

        userRepository.findByPassportNumber(request.getPassportNumber())
            .filter(existing -> !existing.getId().equals(user.getId()))
            .ifPresent(existing -> {
                throw new BadRequestException("Passport number is already registered");
            });

        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setPassportNumber(request.getPassportNumber());
        user.setNationality(request.getNationality());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setProfilePhotoUrl(request.getProfilePhotoUrl());

        User savedUser = userRepository.save(user);
        return toProfileResponse(savedUser);
    }

    /**
     * Changes user password after current password verification.
     */
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * Deactivates the authenticated user account.
     */
    public void deactivateAccount(String email) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        user.setActive(false);
        userRepository.save(user);
    }

    /**
     * Returns profile summary for all users; used by admin role.
     */
    public List<ProfileResponse> getAllUsers() {
        return userRepository.findAll()
            .stream()
            .map(this::toProfileResponse)
            .toList();
    }

    /**
     * Returns registered email for an existing user id.
     * Used by internal service-to-service notification delivery.
     */
    public String getUserEmailById(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return normalizeEmail(user.getEmail());
    }

    /**
     * Validates token presence, blacklist state, and expiry/signature.
     */
    private void validateIncomingToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Authorization token is required");
        }
        if (tokenBlacklistService.isBlacklisted(token)) {
            throw new BadRequestException("Token is already invalidated");
        }
        if (!jwtService.isTokenValid(token)) {
            throw new BadRequestException("Token is invalid or expired");
        }
    }

    private ProfileResponse toProfileResponse(User user) {
        return new ProfileResponse(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getPhone(),
            user.getPassportNumber(),
            user.getNationality(),
            user.getDateOfBirth(),
            user.getProfilePhotoUrl(),
            user.getRole().name(),
            user.getProvider(),
            user.isActive()
        );
    }

    private void createAndSendOtp(User user) {
        List<PasswordResetToken> activeTokens = passwordResetTokenRepository.findByEmailAndUsedFalse(user.getEmail());
        activeTokens.forEach(token -> token.setUsed(true));
        if (!activeTokens.isEmpty()) {
            passwordResetTokenRepository.saveAll(activeTokens);
        }

        String otp = String.format("%06d", random.nextInt(1_000_000));
        PasswordResetToken token = new PasswordResetToken();
        token.setEmail(user.getEmail());
        token.setOtpCode(otp);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(passwordResetExpiryMinutes));
        token.setUsed(false);
        passwordResetTokenRepository.save(token);

        sendOtpMail(user.getEmail(), user.getFullName(), otp);
    }

    private void sendOtpMail(String email, String fullName, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(email);
            if (mailFrom != null && !mailFrom.isBlank()) {
                helper.setFrom(mailFrom);
            }
            helper.setSubject("SkyBooker Password Reset OTP");
            helper.setText(buildOtpHtml(fullName, otp), true);
            mailSender.send(message);
        } catch (MessagingException exception) {
            LOGGER.error("Unable to send password reset OTP to {}", email, exception);
            throw new BadRequestException("Unable to send reset OTP right now. Please try again.");
        }
    }

    private String buildOtpHtml(String fullName, String otp) {
        String safeName = fullName == null || fullName.isBlank() ? "Traveler" : fullName;
        return "<div style='font-family:Arial,sans-serif;color:#10203d'>"
            + "<h2>SkyBooker Password Reset</h2>"
            + "<p>Hello " + safeName + ",</p>"
            + "<p>Use this OTP to reset your password:</p>"
            + "<p style='font-size:28px;font-weight:700;letter-spacing:4px;color:#1d62ec;'>" + otp + "</p>"
            + "<p>This OTP expires in " + passwordResetExpiryMinutes + " minutes.</p>"
            + "<p>If you did not request this, you can ignore this email.</p>"
            + "</div>";
    }

    private Map<String, Object> validateGoogleToken(String idToken) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken,
            HttpMethod.GET,
            HttpEntity.EMPTY,
            new org.springframework.core.ParameterizedTypeReference<>() {
            }
        );

        Map<String, Object> payload = response.getBody();
        if (payload == null) {
            throw new BadRequestException("Invalid Google token");
        }

        String audience = String.valueOf(payload.getOrDefault("aud", ""));
        if (googleClientId != null && !googleClientId.isBlank() && !googleClientId.equals(audience)) {
            throw new BadRequestException("Google token is not issued for this application");
        }

        String emailVerified = String.valueOf(payload.getOrDefault("email_verified", "false"));
        if (!"true".equalsIgnoreCase(emailVerified)) {
            throw new BadRequestException("Google email is not verified");
        }

        return payload;
    }

    private User buildGoogleUser(Map<String, Object> tokenPayload, String email, UserRole role) {
        String fullName = String.valueOf(tokenPayload.getOrDefault("name", "SkyBooker User"));
        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("GoogleLogin@" + random.nextInt(10_000)));
        user.setPhone(generatePlaceholderPhone());
        user.setPassportNumber(generatePlaceholderPassport());
        user.setNationality("Unknown");
        user.setRole(role == null ? UserRole.PASSENGER : role);
        user.setProvider("GOOGLE");
        user.setActive(true);
        user.setProfilePhotoUrl(String.valueOf(tokenPayload.getOrDefault("picture", "")));
        return user;
    }

    private String generatePlaceholderPhone() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = "9" + String.format("%09d", random.nextInt(1_000_000_000));
            if (!userRepository.existsByPhone(candidate)) {
                return candidate;
            }
        }
        return "9" + System.currentTimeMillis() % 1_000_000_000L;
    }

    private String generatePlaceholderPassport() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = "G" + String.format("%07d", random.nextInt(10_000_000));
            if (!userRepository.existsByPassportNumber(candidate)) {
                return candidate;
            }
        }
        return "G" + System.currentTimeMillis() % 10_000_000L;
    }

    private void assignStaffAirlineIfApplicable(User user, UserRole role, Long airlineId) {
        if (role != UserRole.AIRLINE_STAFF) {
            return;
        }
        if (airlineId == null || airlineId <= 0) {
            throw new BadRequestException("airlineId is required for airline staff accounts");
        }

        Map<String, Object> request = Map.of("airlineId", airlineId);
        restTemplate.exchange(
            airlineAirportBaseUrl + "/staff-airlines/" + user.getId(),
            HttpMethod.PUT,
            new HttpEntity<>(request),
            new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
            }
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
