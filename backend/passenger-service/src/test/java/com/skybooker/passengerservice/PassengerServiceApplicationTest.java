package com.skybooker.passengerservice;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PassengerServiceApplicationTest {

    @Test
    void shouldCreateRestTemplateBean() {
        RestTemplate restTemplate = new PassengerServiceApplication().restTemplate();
        assertNotNull(restTemplate);
    }
}
