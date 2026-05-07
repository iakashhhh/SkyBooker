package com.skybooker.bookingservice.controller;

import com.skybooker.bookingservice.dto.BookingResponse;
import com.skybooker.bookingservice.dto.CancelBookingRequest;
import com.skybooker.bookingservice.dto.CreateBookingRequest;
import com.skybooker.bookingservice.dto.UpdateBookingFareRequest;
import com.skybooker.bookingservice.dto.UpdateBookingStatusRequest;
import com.skybooker.bookingservice.entity.BookingStatus;
import com.skybooker.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Day 5 booking APIs.
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public BookingResponse createBooking(@Valid @RequestBody CreateBookingRequest request) {
        return bookingService.createBooking(request);
    }

    @GetMapping("/{bookingId}")
    public BookingResponse getBookingById(@PathVariable String bookingId) {
        return bookingService.getBookingById(bookingId);
    }

    @GetMapping
    public List<BookingResponse> getAllBookings() {
        return bookingService.getAllBookings();
    }

    @GetMapping("/user/{userId}")
    public List<BookingResponse> getBookingsByUser(@PathVariable Long userId) {
        return bookingService.getBookingsByUser(userId);
    }

    @GetMapping("/pnr/{pnr}")
    public BookingResponse getBookingByPnr(@PathVariable("pnr") String pnrCode) {
        return bookingService.getBookingByPnr(pnrCode);
    }

    @PutMapping("/cancel")
    public BookingResponse cancelBooking(@Valid @RequestBody CancelBookingRequest request) {
        return bookingService.cancelBooking(request);
    }

    @PutMapping("/{bookingId}/fare")
    public BookingResponse updateBookingFare(@PathVariable String bookingId,
                                             @Valid @RequestBody UpdateBookingFareRequest request) {
        return bookingService.updateBookingFare(bookingId, request);
    }

    @PutMapping("/{bookingId}/status")
    public ResponseEntity<Void> updateBookingStatus(@PathVariable String bookingId,
                                                    @Valid @RequestBody UpdateBookingStatusRequest request) {
        bookingService.updateBookingStatus(bookingId, BookingStatus.valueOf(request.getStatus().toUpperCase()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{bookingId}/ticket/pdf")
    public ResponseEntity<byte[]> downloadTicketPdf(@PathVariable String bookingId) {
        byte[] pdfBytes = bookingService.generateTicketPdf(bookingId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ticket-" + bookingId + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfBytes);
    }

    @GetMapping("/{bookingId}/ticket/qr")
    public ResponseEntity<byte[]> getTicketQr(@PathVariable String bookingId) {
        byte[] pngBytes = bookingService.generateTicketQr(bookingId);
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(pngBytes);
    }
}
