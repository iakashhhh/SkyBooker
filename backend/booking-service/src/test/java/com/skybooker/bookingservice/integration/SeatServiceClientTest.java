package com.skybooker.bookingservice.integration;

import com.skybooker.bookingservice.integration.dto.SeatActionRequest;
import com.skybooker.bookingservice.integration.dto.SeatActionResponse;
import com.skybooker.bookingservice.integration.dto.SeatMapResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeatServiceClientTest {

    @Test
    void shouldCallSeatEndpoints() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        SeatServiceClient client = new SeatServiceClient(restTemplate, "http://seat-service");

        SeatActionRequest request = new SeatActionRequest();
        SeatActionResponse action = new SeatActionResponse();
        SeatMapResponse map = new SeatMapResponse();

        when(restTemplate.postForObject("http://seat-service/seats/hold", request, SeatActionResponse.class)).thenReturn(action);
        when(restTemplate.getForObject("http://seat-service/seats/1", SeatMapResponse.class)).thenReturn(map);

        assertSame(action, client.holdSeats(request));
        assertSame(map, client.getSeatMap(1L));

        client.confirmSeats(request);
        client.releaseSeats(request);

        verify(restTemplate).postForObject("http://seat-service/seats/confirm", request, SeatActionResponse.class);
        verify(restTemplate).postForObject("http://seat-service/seats/release", request, SeatActionResponse.class);
        verify(restTemplate).getForObject("http://seat-service/seats/1", SeatMapResponse.class);
    }
}
