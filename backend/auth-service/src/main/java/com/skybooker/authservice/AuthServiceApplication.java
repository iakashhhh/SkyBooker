package com.skybooker.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * This is the Auth/User service bootstrap class.
 * In Day 1, it provides a simple structure and registers itself with Eureka.
 */
@SpringBootApplication
public class AuthServiceApplication {

    /**
     * Starts the Auth Service application.
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}