package com.skybooker.authservice.repository;

import com.skybooker.authservice.entity.User;
import com.skybooker.authservice.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * This repository handles persistence operations for user records.
 * It exposes lookup methods used by authentication workflows.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    
     // Finds a user by email for login and JWT authentication. 
    Optional<User> findByEmail(String email);

    
     // Checks whether an email is already registered.
    boolean existsByEmail(String email);

    
     // Checks whether a passport number is already registered.   
    boolean existsByPassportNumber(String passportNumber);

    /**
     * Finds user by phone for profile uniqueness checks.
     */
    Optional<User> findByPhone(String phone);

    /**
     * Finds user by passport number for profile uniqueness checks.
     */
    Optional<User> findByPassportNumber(String passportNumber);

    /**
     * Returns all users filtered by role.
     */
    List<User> findAllByRole(UserRole role);

    /**
     * Checks whether a phone number is already registered.
     */
    boolean existsByPhone(String phone);
}
