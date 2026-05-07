package com.skybooker.flightservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * This is the Flight service bootstrap class.
 * In Day 1, this service is scaffolded and registered to Eureka.
 */
@SpringBootApplication
public class FlightServiceApplication {

    /**
     * Starts the Flight Service application.
     */
    public static void main(String[] args) {
        SpringApplication.run(FlightServiceApplication.class, args);
    }
}
