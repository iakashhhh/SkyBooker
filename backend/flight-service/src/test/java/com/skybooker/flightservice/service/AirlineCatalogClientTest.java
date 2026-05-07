package com.skybooker.flightservice.service;

import com.skybooker.flightservice.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AirlineCatalogClientTest {

    @Mock
    private RestTemplate restTemplate;

    private AirlineCatalogClient client;

    @BeforeEach
    void setUp() {
        client = new AirlineCatalogClient(restTemplate, "http://localhost:8080");
    }

    @Test
    void validateAirlineExists_shouldSucceedWhenUpstreamReturnsAirline() {
        when(restTemplate.getForObject("http://localhost:8080/airlines/7", Map.class))
            .thenReturn(Map.of("airlineId", 7));

        client.validateAirlineExists(7L);

        verify(restTemplate).getForObject("http://localhost:8080/airlines/7", Map.class);
    }

    @Test
    void validateAirlineExists_shouldThrowBadRequestWhenUpstreamFails() {
        when(restTemplate.getForObject("http://localhost:8080/airlines/9", Map.class))
            .thenThrow(new RuntimeException("downstream error"));

        assertThrows(BadRequestException.class, () -> client.validateAirlineExists(9L));
    }

    @Test
    void fetchAirlines_shouldReturnEmptyListWhenUpstreamReturnsNull() {
        when(restTemplate.getForObject("http://localhost:8080/airlines", List.class)).thenReturn(null);

        List<Map<String, Object>> result = client.fetchAirlines();

        assertEquals(0, result.size());
    }
}
