package com.skybooker.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * This service is the single entry point for all frontend API requests.
 * It discovers backend services from Eureka and forwards requests
 * to the correct microservice using configured route rules.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    /**
     * Starts the API Gateway application.
     */
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
