package com.skybooker.flightservice.service;

import com.skybooker.flightservice.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class AirlineCatalogClient {

    private final RestTemplate restTemplate;
    private final String airlineAirportBaseUrl;

    public AirlineCatalogClient(RestTemplate restTemplate,
                                @Value("${flight.upstream.airline-airport-base-url:http://localhost:8080}") String airlineAirportBaseUrl) {
        this.restTemplate = restTemplate;
        this.airlineAirportBaseUrl = airlineAirportBaseUrl;
    }

    public void validateAirlineExists(Long airlineId) {
        try {
            restTemplate.getForObject(airlineAirportBaseUrl + "/airlines/" + airlineId, Map.class);
        } catch (RuntimeException exception) {
            throw new BadRequestException("Invalid airlineId: " + airlineId);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchAirlines() {
        List<Map<String, Object>> airlines = restTemplate.getForObject(airlineAirportBaseUrl + "/airlines", List.class);
        return airlines == null ? List.of() : airlines;
    }
}
