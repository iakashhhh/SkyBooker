package com.skybooker.passengerservice.controller;

import com.skybooker.passengerservice.dto.AssignSeatRequest;
import com.skybooker.passengerservice.dto.PassengerRequest;
import com.skybooker.passengerservice.dto.PassengerResponse;
import com.skybooker.passengerservice.service.PassengerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerControllerTest {

    @Mock
    private PassengerService passengerService;

    private PassengerController controller;

    @BeforeEach
    void setUp() {
        controller = new PassengerController(passengerService);
    }

    @Test
    void shouldDelegateAllPassengerCrudEndpoints() {
        PassengerRequest request = new PassengerRequest();
        PassengerResponse response = new PassengerResponse();
        when(passengerService.addPassenger(request)).thenReturn(response);
        when(passengerService.getPassengerById(10L)).thenReturn(response);
        when(passengerService.getByPassportNumber("P123")).thenReturn(response);
        when(passengerService.updatePassenger(10L, request)).thenReturn(response);

        assertEquals(response, controller.addPassenger(request));
        assertEquals(response, controller.getPassenger(10L));
        assertEquals(response, controller.getByPassport("P123"));
        assertEquals(response, controller.updatePassenger(10L, request));
        assertEquals(response, controller.updatePassengerViaStrictPath(10L, request));
    }

    @Test
    void shouldDelegateSeatAndBookingOperations() {
        AssignSeatRequest seatRequest = new AssignSeatRequest();
        PassengerResponse response = new PassengerResponse();
        when(passengerService.assignSeat(77L, seatRequest)).thenReturn(response);
        when(passengerService.getPassengersByBooking("BKG-1")).thenReturn(List.of(response));
        when(passengerService.getPassengerCount("BKG-1")).thenReturn(3L);

        assertEquals(response, controller.assignSeat(77L, seatRequest));
        assertEquals(1, controller.getPassengersByBooking("BKG-1").size());
        assertEquals(3L, controller.getCountByBooking("BKG-1").get("count"));

        controller.deletePassenger(11L);
        controller.deleteByBooking("BKG-1");
        controller.clearSeatAssignments("BKG-1");

        verify(passengerService).deletePassenger(11L);
        verify(passengerService).deletePassengersByBooking("BKG-1");
        verify(passengerService).clearSeatAssignmentsByBooking("BKG-1");
    }
}
