package com.skybooker.flightservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class HttpClientConfigTest {

    @Test
    void shouldBuildRestTemplate() {
        HttpClientConfig config = new HttpClientConfig();
        assertNotNull(config.restTemplate(new RestTemplateBuilder()));
    }
}
