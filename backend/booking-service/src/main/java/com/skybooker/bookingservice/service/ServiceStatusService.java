package com.skybooker.bookingservice.service;

import com.skybooker.bookingservice.dto.ServiceStatusResponse;
import org.springframework.stereotype.Service;

/**
 * This service contains Day 1 starter business logic.
 * It returns simple status details for basic flow checks.
 */
@Service
public class ServiceStatusService {

    /**
     * Returns the current status information.
     */
    public ServiceStatusResponse getStatus() {
        return new ServiceStatusResponse("booking-service", "UP");
    }
}
