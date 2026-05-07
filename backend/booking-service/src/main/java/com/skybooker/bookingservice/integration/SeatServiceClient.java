package com.skybooker.bookingservice.integration;

import com.skybooker.bookingservice.integration.dto.SeatActionRequest;
import com.skybooker.bookingservice.integration.dto.SeatActionResponse;
import com.skybooker.bookingservice.integration.dto.SeatMapResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Lightweight HTTP client for seat hold/release APIs.
 */
@Component
public class SeatServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public SeatServiceClient(RestTemplate restTemplate,
                             @Value("${integration.seat-service-base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public SeatActionResponse holdSeats(SeatActionRequest request) {
        return restTemplate.postForObject(baseUrl + "/seats/hold", request, SeatActionResponse.class);
    }

    public SeatMapResponse getSeatMap(Long flightId) {
        return restTemplate.getForObject(baseUrl + "/seats/" + flightId, SeatMapResponse.class);
    }

    public void confirmSeats(SeatActionRequest request) {
        restTemplate.postForObject(baseUrl + "/seats/confirm", request, SeatActionResponse.class);
    }

    public void releaseSeats(SeatActionRequest request) {
        restTemplate.postForObject(baseUrl + "/seats/release", request, SeatActionResponse.class);
    }
}
