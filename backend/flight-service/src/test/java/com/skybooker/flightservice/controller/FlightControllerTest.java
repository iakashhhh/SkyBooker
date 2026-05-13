package com.skybooker.flightservice.controller;

import com.skybooker.flightservice.dto.ApiMessageResponse;
import com.skybooker.flightservice.dto.CreateFlightRequest;
import com.skybooker.flightservice.dto.FlightResponse;
import com.skybooker.flightservice.dto.RoundTripSearchResponse;
import com.skybooker.flightservice.dto.UpdateFlightAvailabilityRequest;
import com.skybooker.flightservice.dto.UpdateFlightRequest;
import com.skybooker.flightservice.dto.UpdateFlightStatusRequest;
import com.skybooker.flightservice.entity.FlightStatus;
import com.skybooker.flightservice.exception.BadRequestException;
import com.skybooker.flightservice.service.FlightService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlightControllerTest {

    private final FlightService flightService = mock(FlightService.class);
    private final FlightController controller = new FlightController(flightService);

    @Test
    void shouldDelegateCrudAndAvailabilityEndpoints() {
        FlightResponse response = sampleFlight();
        CreateFlightRequest createRequest = new CreateFlightRequest();
        UpdateFlightRequest updateRequest = new UpdateFlightRequest();
        UpdateFlightStatusRequest statusRequest = new UpdateFlightStatusRequest();
        UpdateFlightAvailabilityRequest availabilityRequest = new UpdateFlightAvailabilityRequest();
        availabilityRequest.setSeatDelta(-2);
        ApiMessageResponse deleteResponse = new ApiMessageResponse("deleted");

        when(flightService.getManagedFlights(any(), eq(8L))).thenReturn(List.of(response));
        when(flightService.addFlight(createRequest)).thenReturn(response);
        when(flightService.updateFlight(10L, updateRequest)).thenReturn(response);
        when(flightService.deleteFlight(10L)).thenReturn(deleteResponse);
        when(flightService.getFlightById(10L)).thenReturn(response);
        when(flightService.adjustAvailableSeats(10L, -2)).thenReturn(response);
        when(flightService.updateFlightStatus(statusRequest)).thenReturn(response);

        assertEquals(1, controller.getManagedFlights(LocalDate.now(), 8L).size());
        assertSame(response, controller.addFlight(createRequest));
        assertSame(response, controller.updateFlight(10L, updateRequest));
        assertSame(deleteResponse, controller.deleteFlight(10L));
        assertSame(response, controller.getFlightById(10L));
        assertSame(response, controller.adjustAvailability(10L, availabilityRequest));
        assertSame(response, controller.updateFlightStatus(statusRequest));

        verify(flightService).getManagedFlights(any(), eq(8L));
        verify(flightService).addFlight(createRequest);
        verify(flightService).updateFlight(10L, updateRequest);
        verify(flightService).deleteFlight(10L);
        verify(flightService).getFlightById(10L);
        verify(flightService).adjustAvailableSeats(10L, -2);
        verify(flightService).updateFlightStatus(statusRequest);
    }

    @Test
    void shouldRequireJourneyDateForOneWaySearch() {
        assertThrows(BadRequestException.class,
            () -> controller.searchOneWayFlights("DEL", "BLR", null, null, null, null, null, null, null, null, null));
    }

    @Test
    void shouldDelegateSearchEndpoints() {
        FlightResponse flight = sampleFlight();
        RoundTripSearchResponse roundTrip = new RoundTripSearchResponse(List.of(flight), List.of(flight));

        when(flightService.searchOneWayFlights(eq("DEL"), eq("BLR"), any(), any(), any(), any(), any(), any(), any(), eq("PRICE")))
            .thenReturn(List.of(flight));
        when(flightService.searchRoundTripFlights(eq("DEL"), eq("BLR"), any(), any(), any(), any(), any(), any(), any(), any(), eq("DURATION")))
            .thenReturn(roundTrip);

        List<FlightResponse> oneWay = controller.searchOneWayFlights(
            "DEL", "BLR", LocalDate.of(2026, 5, 10), null, null, null, null, null, null, null, "PRICE");
        RoundTripSearchResponse rt = controller.searchRoundTripFlights(
            "DEL", "BLR", LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12), null, null, null, null, null, null, "DURATION");

        assertEquals(1, oneWay.size());
        assertSame(roundTrip, rt);
        verify(flightService).searchOneWayFlights(eq("DEL"), eq("BLR"), eq(LocalDate.of(2026, 5, 10)), any(), any(), any(), any(), any(), any(), eq("PRICE"));
        verify(flightService).searchRoundTripFlights(eq("DEL"), eq("BLR"), eq(LocalDate.of(2026, 5, 10)), eq(LocalDate.of(2026, 5, 12)), any(), any(), any(), any(), any(), any(), eq("DURATION"));
    }

    private FlightResponse sampleFlight() {
        FlightResponse response = new FlightResponse();
        response.setFlightId(10L);
        response.setFlightNumber("AI-101");
        response.setStatus(FlightStatus.ON_TIME);
        return response;
    }
}
