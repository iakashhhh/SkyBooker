package com.skybooker.passengerservice.integration;

import com.skybooker.passengerservice.exception.BadRequestException;
import com.skybooker.passengerservice.integration.dto.BookingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class BookingServiceClientTest {

    private RestTemplate restTemplate;
    private BookingServiceClient bookingServiceClient;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        bookingServiceClient = new BookingServiceClient(restTemplate, "http://booking");
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void shouldReturnBookingWhenResponseIsValid() {
        server.expect(requestTo("http://booking/api/v1/bookings/BKG-1"))
            .andExpect(method(GET))
            .andRespond(withSuccess("{\"bookingId\":\"BKG-1\"}", MediaType.APPLICATION_JSON));

        BookingResponse response = bookingServiceClient.getBookingById("BKG-1");
        assertEquals("BKG-1", response.getBookingId());
    }

    @Test
    void shouldThrowWhenBookingPayloadIsInvalid() {
        server.expect(requestTo("http://booking/api/v1/bookings/BKG-2"))
            .andExpect(method(GET))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(BadRequestException.class, () -> bookingServiceClient.getBookingById("BKG-2"));
    }

    @Test
    void shouldThrowWhenBookingServiceIsUnavailable() {
        server.expect(requestTo("http://booking/api/v1/bookings/BKG-3"))
            .andExpect(method(GET))
            .andRespond(withServerError());

        assertThrows(BadRequestException.class, () -> bookingServiceClient.getBookingById("BKG-3"));
    }
}
