package com.skybooker.adminserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * This service represents the admin backend entry point.
 * In Day 1, it only registers with Eureka so the infrastructure
 * is ready for upcoming admin management APIs.
 */
@SpringBootApplication
public class AdminServerApplication {

    /**
     * Starts the Admin Server application.
     */
    public static void main(String[] args) {
        SpringApplication.run(AdminServerApplication.class, args);
    }
}
