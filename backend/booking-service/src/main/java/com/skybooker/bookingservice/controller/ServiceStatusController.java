package com.skybooker.bookingservice.controller;

import com.skybooker.bookingservice.dto.ServiceStatusResponse;
import com.skybooker.bookingservice.service.ServiceStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * This controller exposes simple Day 1 endpoints for booking-service.
 * It demonstrates Controller -> Service flow with readable code.
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class ServiceStatusController {

    private final ServiceStatusService serviceStatusService;

    public ServiceStatusController(ServiceStatusService serviceStatusService) {
        this.serviceStatusService = serviceStatusService;
    }

    /**
     * Returns status details for the Booking service.
     */
    @GetMapping("/status")
    public ServiceStatusResponse getStatus() {
        return serviceStatusService.getStatus();
    }
}
