package com.skybooker.bookingservice.controller;

import com.skybooker.bookingservice.dto.ServiceStatusResponse;
import com.skybooker.bookingservice.service.ServiceStatusService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceStatusControllerTest {

    @Test
    void shouldReturnServiceStatusFromService() {
        ServiceStatusService service = mock(ServiceStatusService.class);
        ServiceStatusController controller = new ServiceStatusController(service);

        ServiceStatusResponse response = new ServiceStatusResponse("booking-service", "UP");
        when(service.getStatus()).thenReturn(response);

        ServiceStatusResponse actual = controller.getStatus();
        assertEquals("booking-service", actual.getService());
        assertEquals("UP", actual.getStatus());
    }
}
