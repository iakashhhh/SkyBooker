package com.skybooker.paymentservice.integration;

import com.skybooker.paymentservice.integration.dto.BookingSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceClientTest {

    @Test
    void shouldCallBookingEndpointsForStatusAndLookup() {
        BookingServiceClient client = new BookingServiceClient("http://booking-service");
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);

        BookingSnapshot snapshot = new BookingSnapshot();
        snapshot.setBookingId("BKG-1");
        when(restTemplate.getForObject("http://booking-service/api/v1/bookings/BKG-1", BookingSnapshot.class)).thenReturn(snapshot);

        client.updateBookingStatus("BKG-1", "CONFIRMED");
        BookingSnapshot actual = client.getBookingById("BKG-1");

        assertSame(snapshot, actual);
        verify(restTemplate).exchange(eq("http://booking-service/api/v1/bookings/BKG-1/status"), eq(org.springframework.http.HttpMethod.PUT), any(org.springframework.http.HttpEntity.class), eq(Void.class));
    }

    @Test
    void shouldReturnNullWhenBookingLookupFails() {
        BookingServiceClient client = new BookingServiceClient("http://booking-service");
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);

        when(restTemplate.getForObject("http://booking-service/api/v1/bookings/MISSING", BookingSnapshot.class))
            .thenThrow(new RestClientException("down"));

        assertNull(client.getBookingById("MISSING"));
    }
}
