package com.skybooker.authservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class HttpClientConfigTest {

    @Test
    void shouldBuildRestTemplate() {
        HttpClientConfig config = new HttpClientConfig();
        RestTemplate template = config.restTemplate(new RestTemplateBuilder());
        assertNotNull(template);
    }
}
