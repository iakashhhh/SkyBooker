package com.skybooker.bookingservice.service;

import com.skybooker.bookingservice.dto.ServiceStatusResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceStatusServiceTest {

    private final ServiceStatusService serviceStatusService = new ServiceStatusService();

    @Test
    void getStatus_shouldReturnServiceUpStatus() {
        ServiceStatusResponse response = serviceStatusService.getStatus();

        assertEquals("booking-service", response.getService());
        assertEquals("UP", response.getStatus());
    }
}
