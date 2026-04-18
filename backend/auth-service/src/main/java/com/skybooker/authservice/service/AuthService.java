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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       TokenBlacklistService tokenBlacklistService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * Registers new user after duplicate checks and password hashing.
     */
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }
        if (userRepository.existsByPassportNumber(request.getPassportNumber())) {
            throw new BadRequestException("Passport number is already registered");
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new BadRequestException("Phone is already registered");
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setPassportNumber(request.getPassportNumber());
        user.setNationality(request.getNationality());
        user.setRole(UserRole.PASSENGER);
        user.setProvider("LOCAL");
        user.setActive(true);

        User savedUser = userRepository.save(user);
        LOGGER.info("User registered successfully with email: {}", savedUser.getEmail());

        String token = jwtService.generateToken(savedUser.getEmail(), savedUser.getRole().name());
        return new AuthResponse(token, savedUser.getEmail(), savedUser.getRole().name(), savedUser.getId());
    }

    /**
     * Authenticates user credentials and returns a fresh JWT token.
     */
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));

        if (!user.isActive()) {
            throw new BadRequestException("Account is deactivated");
        }

        LOGGER.info("User logged in successfully with email: {}", user.getEmail());
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user.getEmail(), user.getRole().name(), user.getId());
    }

    /**
     * Refreshes JWT token if provided token is valid and not blacklisted.
     */
    public AuthResponse refreshToken(String token) {
        validateIncomingToken(token);
        String email = jwtService.extractEmail(token);
        String role = jwtService.extractRole(token);
        User user = userRepository.findByEmail(email)
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
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        return new ProfileResponse(
            user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getPassportNumber(),
                user.getNationality(),
                user.getRole().name(),
                user.getProvider(),
                user.isActive()
        );
    }

    /**
     * Updates profile fields for currently authenticated user.
     */
    public ProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
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
        User savedUser = userRepository.save(user);

        return new ProfileResponse(
            savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedUser.getPhone(),
                savedUser.getPassportNumber(),
                savedUser.getNationality(),
                savedUser.getRole().name(),
                savedUser.getProvider(),
                savedUser.isActive()
        );
    }

    /**
     * Changes user password after current password verification.
     */
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
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
        User user = userRepository.findByEmail(email)
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
                .map(user -> new ProfileResponse(
                    user.getId(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getPhone(),
                        user.getPassportNumber(),
                        user.getNationality(),
                        user.getRole().name(),
                        user.getProvider(),
                        user.isActive()
                ))
                .toList();
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
}
