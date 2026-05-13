package com.skybooker.bookingservice.controller;

import com.skybooker.bookingservice.dto.CancelBookingRequest;
import com.skybooker.bookingservice.dto.CreateBookingRequest;
import com.skybooker.bookingservice.dto.UpdateBookingFareRequest;
import com.skybooker.bookingservice.dto.UpdateBookingStatusRequest;
import com.skybooker.bookingservice.entity.BookingStatus;
import com.skybooker.bookingservice.service.BookingService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingControllerTest {

    private final BookingService bookingService = mock(BookingService.class);
    private final BookingController controller = new BookingController(bookingService);

    @Test
    void shouldDelegateCrudAndTicketEndpoints() {
        CreateBookingRequest create = new CreateBookingRequest();
        CancelBookingRequest cancel = new CancelBookingRequest();
        UpdateBookingFareRequest fare = new UpdateBookingFareRequest();
        UpdateBookingStatusRequest status = new UpdateBookingStatusRequest();
        status.setStatus("confirmed");

        var bookingResponse = new com.skybooker.bookingservice.dto.BookingResponse();
        bookingResponse.setBookingId("BKG-1");

        when(bookingService.createBooking(create)).thenReturn(bookingResponse);
        when(bookingService.getBookingById("BKG-1")).thenReturn(bookingResponse);
        when(bookingService.getAllBookings()).thenReturn(List.of(bookingResponse));
        when(bookingService.getBookingsByUser(9L)).thenReturn(List.of(bookingResponse));
        when(bookingService.getBookingByPnr("PNR1")).thenReturn(bookingResponse);
        when(bookingService.cancelBooking(cancel)).thenReturn(bookingResponse);
        when(bookingService.updateBookingFare("BKG-1", fare)).thenReturn(bookingResponse);
        when(bookingService.generateTicketPdf("BKG-1")).thenReturn(new byte[]{1, 2});
        when(bookingService.generateTicketQr("BKG-1")).thenReturn(new byte[]{3, 4});

        assertEquals("BKG-1", controller.createBooking(create).getBookingId());
        assertEquals("BKG-1", controller.getBookingById("BKG-1").getBookingId());
        assertEquals(1, controller.getAllBookings().size());
        assertEquals(1, controller.getBookingsByUser(9L).size());
        assertEquals("BKG-1", controller.getBookingByPnr("PNR1").getBookingId());
        assertEquals("BKG-1", controller.cancelBooking(cancel).getBookingId());
        assertEquals("BKG-1", controller.updateBookingFare("BKG-1", fare).getBookingId());
        assertEquals(204, controller.updateBookingStatus("BKG-1", status).getStatusCode().value());
        assertArrayEquals(new byte[]{1, 2}, controller.downloadTicketPdf("BKG-1").getBody());
        assertArrayEquals(new byte[]{3, 4}, controller.getTicketQr("BKG-1").getBody());

        verify(bookingService).updateBookingStatus("BKG-1", BookingStatus.CONFIRMED);
    }
}
