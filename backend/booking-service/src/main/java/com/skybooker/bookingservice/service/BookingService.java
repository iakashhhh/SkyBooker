package com.skybooker.bookingservice.service;

import com.skybooker.bookingservice.dto.BookingResponse;
import com.skybooker.bookingservice.dto.CancelBookingRequest;
import com.skybooker.bookingservice.dto.CreateBookingRequest;
import com.skybooker.bookingservice.dto.UpdateBookingFareRequest;
import com.skybooker.bookingservice.entity.BookingStatus;

import java.util.List;

/**
 *  booking service contract.
 */
public interface BookingService {

    BookingResponse createBooking(CreateBookingRequest request);

    BookingResponse getBookingById(String bookingId);

    List<BookingResponse> getAllBookings();

    List<BookingResponse> getBookingsByUser(Long userId);

    BookingResponse getBookingByPnr(String pnrCode);

    BookingResponse cancelBooking(CancelBookingRequest request);

    BookingResponse updateBookingFare(String bookingId, UpdateBookingFareRequest request);

    void updateBookingStatus(String bookingId, BookingStatus status);

    byte[] generateTicketPdf(String bookingId);

    byte[] generateTicketQr(String bookingId);
}
