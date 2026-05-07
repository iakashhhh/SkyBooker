package com.skybooker.bookingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * This is the Booking service bootstrap class.
 * In Day 1, it exposes a basic endpoint and registers with Eureka.
 */
@SpringBootApplication
public class BookingServiceApplication {

    /**
     * Starts the Booking Service application.
     */
    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
