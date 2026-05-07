package com.skybooker.bookingservice.service;

import com.skybooker.bookingservice.dto.BookingResponse;
import com.skybooker.bookingservice.dto.CancelBookingRequest;
import com.skybooker.bookingservice.dto.CreateBookingRequest;
import com.skybooker.bookingservice.dto.PaymentResponse;
import com.skybooker.bookingservice.dto.UpdateBookingFareRequest;
import com.skybooker.bookingservice.entity.Booking;
import com.skybooker.bookingservice.entity.BookingStatus;
import com.skybooker.bookingservice.entity.TripType;
import com.skybooker.bookingservice.exception.BadRequestException;
import com.skybooker.bookingservice.exception.ResourceNotFoundException;
import com.skybooker.bookingservice.integration.FlightServiceClient;
import com.skybooker.bookingservice.integration.PaymentServiceClient;
import com.skybooker.bookingservice.integration.SeatServiceClient;
import com.skybooker.bookingservice.integration.dto.FlightResponse;
import com.skybooker.bookingservice.integration.dto.SeatActionRequest;
import com.skybooker.bookingservice.integration.dto.SeatMapResponse;
import com.skybooker.bookingservice.repository.BookingRepository;
import com.skybooker.bookingservice.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/**
 * Tests for booking creation and PNR uniqueness.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private FlightServiceClient flightServiceClient;

    @Mock
    private SeatServiceClient seatServiceClient;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    private BookingServiceImpl bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingServiceImpl(
            bookingRepository,
            flightServiceClient,
            seatServiceClient,
            paymentServiceClient,
            new BigDecimal("0.18"),
            new BigDecimal("350"),
            new BigDecimal("120")
        );
    }

    @Test
    void createBooking_shouldPersistBookingAndReturnResponse() {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setUserId(101L);
        request.setFlightId(44L);
        request.setTripType(TripType.ONE_WAY);
        request.setBaseFare(new BigDecimal("4500"));
        request.setSeatIds(List.of(11L, 12L));
        request.setMealPreference("VEG");
        request.setLuggageKg(3);
        request.setContactEmail("u@example.com");
        request.setContactPhone("9999999999");

        FlightResponse flightResponse = new FlightResponse();
        flightResponse.setFlightId(44L);
        flightResponse.setAvailableSeats(9);
        when(flightServiceClient.getFlightById(44L)).thenReturn(flightResponse);
        when(seatServiceClient.getSeatMap(44L)).thenReturn(heldSeatMap(44L, List.of(11L, 12L)));
        when(paymentServiceClient.createPayment(any())).thenReturn(paymentResponse("pay-101"));

        when(bookingRepository.existsByPnrCode(any())).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setBookingId("bkg-1");
            booking.setBookedAt(LocalDateTime.now());
            return booking;
        });

        BookingResponse response = bookingService.createBooking(request);

        assertNotNull(response.getBookingId());
        assertEquals(BookingStatus.PENDING, response.getStatus());
        assertEquals(List.of(11L, 12L), response.getSeatIds());
        assertEquals("pay-101", response.getPaymentId());
        verify(seatServiceClient, never()).holdSeats(any(SeatActionRequest.class));
        verify(seatServiceClient).getSeatMap(44L);
        verify(paymentServiceClient).createPayment(any());
    }

    @Test
    void updateBookingStatus_shouldConfirmSeatsForSuccessfulPayment() {
        Booking booking = new Booking();
        booking.setBookingId("bkg-22");
        booking.setFlightId(44L);
        booking.setSelectedSeatIds("11,12");
        booking.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findById("bkg-22")).thenReturn(java.util.Optional.of(booking));

        bookingService.updateBookingStatus("bkg-22", BookingStatus.CONFIRMED);

        ArgumentCaptor<SeatActionRequest> seatCaptor = ArgumentCaptor.forClass(SeatActionRequest.class);
        verify(seatServiceClient).confirmSeats(seatCaptor.capture());
        assertEquals(List.of(11L, 12L), seatCaptor.getValue().getSeatIds());
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void createBooking_shouldRejectWhenSelectedSeatIsNotHeld() {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setUserId(101L);
        request.setFlightId(44L);
        request.setTripType(TripType.ONE_WAY);
        request.setBaseFare(new BigDecimal("4500"));
        request.setSeatIds(List.of(11L));
        request.setLuggageKg(0);
        request.setContactEmail("u@example.com");
        request.setContactPhone("9999999999");

        when(seatServiceClient.getSeatMap(44L)).thenReturn(availableSeatMap(44L, 11L));

        org.junit.jupiter.api.Assertions.assertThrows(
            com.skybooker.bookingservice.exception.ConflictException.class,
            () -> bookingService.createBooking(request)
        );
        verify(paymentServiceClient, never()).createPayment(any());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void generatePnr_shouldBeUniqueAcrossMultipleCalls() {
        when(bookingRepository.existsByPnrCode(any())).thenReturn(false);

        Set<String> pnrs = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String pnr = bookingService.generateUniquePnr();
            assertEquals(6, pnr.length());
            assertTrue(pnrs.add(pnr));
        }
    }

    @Test
    void createBooking_shouldRejectDuplicateSeatIds() {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setUserId(101L);
        request.setFlightId(44L);
        request.setTripType(TripType.ONE_WAY);
        request.setBaseFare(new BigDecimal("4500"));
        request.setSeatIds(List.of(11L, 11L));
        request.setLuggageKg(0);
        request.setContactEmail("u@example.com");
        request.setContactPhone("9999999999");

        assertThrows(BadRequestException.class, () -> bookingService.createBooking(request));
        verify(seatServiceClient, never()).getSeatMap(any());
        verify(paymentServiceClient, never()).createPayment(any());
    }

    @Test
    void cancelBooking_shouldRejectAlreadyCancelledBooking() {
        Booking booking = new Booking();
        booking.setBookingId("bkg-cancelled");
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById("bkg-cancelled")).thenReturn(java.util.Optional.of(booking));

        CancelBookingRequest request = new CancelBookingRequest();
        request.setBookingId("bkg-cancelled");

        assertThrows(BadRequestException.class, () -> bookingService.cancelBooking(request));
    }

    @Test
    void updateBookingFare_shouldRecalculateAndRefreshPendingPayment() {
        Booking booking = new Booking();
        booking.setBookingId("bkg-fare");
        booking.setUserId(22L);
        booking.setFlightId(44L);
        booking.setSelectedSeatIds("11");
        booking.setStatus(BookingStatus.PENDING);
        booking.setBaseFare(new BigDecimal("2500"));
        booking.setSeatCharge(new BigDecimal("100"));
        booking.setTaxes(new BigDecimal("450"));
        booking.setMealCharge(BigDecimal.ZERO);
        booking.setBaggageCharge(BigDecimal.ZERO);
        booking.setTotalFare(new BigDecimal("3050"));
        when(bookingRepository.findById("bkg-fare")).thenReturn(java.util.Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentServiceClient.createPayment(any())).thenReturn(paymentResponse("pay-updated"));

        UpdateBookingFareRequest request = new UpdateBookingFareRequest();
        request.setLuggageKg(2);
        UpdateBookingFareRequest.MealSelection mealSelection = new UpdateBookingFareRequest.MealSelection();
        mealSelection.setMealName("VEG");
        mealSelection.setMealPrice(new BigDecimal("200"));
        request.setMealSelections(List.of(mealSelection));

        BookingResponse response = bookingService.updateBookingFare("bkg-fare", request);

        assertEquals("pay-updated", response.getPaymentId());
        assertEquals(2, response.getLuggageKg());
        assertEquals("VEG", response.getMealPreference());
        verify(paymentServiceClient).createPayment(any());
    }

    @Test
    void getBookingById_shouldThrowWhenBookingMissing() {
        when(bookingRepository.findById("missing")).thenReturn(java.util.Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bookingService.getBookingById("missing"));
    }

    @Test
    void updateBookingStatus_shouldSkipWhenStatusIsUnchanged() {
        Booking booking = new Booking();
        booking.setBookingId("bkg-same");
        booking.setFlightId(44L);
        booking.setSelectedSeatIds("11,12");
        booking.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findById("bkg-same")).thenReturn(java.util.Optional.of(booking));

        bookingService.updateBookingStatus("bkg-same", BookingStatus.PENDING);

        verify(bookingRepository, never()).save(any(Booking.class));
        verify(seatServiceClient, never()).confirmSeats(any(SeatActionRequest.class));
        verify(seatServiceClient, never()).releaseSeats(any(SeatActionRequest.class));
        verify(flightServiceClient, never()).adjustAvailableSeats(anyLong(), anyInt());
    }

    @Test
    void updateBookingStatus_shouldReleaseSeatsWhenCancelledFromPending() {
        Booking booking = new Booking();
        booking.setBookingId("bkg-cancel");
        booking.setFlightId(44L);
        booking.setSelectedSeatIds("11,12");
        booking.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findById("bkg-cancel")).thenReturn(java.util.Optional.of(booking));

        bookingService.updateBookingStatus("bkg-cancel", BookingStatus.CANCELLED);

        verify(seatServiceClient, times(1)).releaseSeats(any(SeatActionRequest.class));
        verify(flightServiceClient, never()).adjustAvailableSeats(anyLong(), anyInt());
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void getBookingsByUser_shouldReturnMappedResponses() {
        Booking booking = new Booking();
        booking.setBookingId("bkg-user-1");
        booking.setUserId(99L);
        booking.setFlightId(44L);
        booking.setSelectedSeatIds("11,12");
        booking.setStatus(BookingStatus.PENDING);
        booking.setPnrCode("PNR123");
        booking.setTripType(TripType.ONE_WAY);
        booking.setBaseFare(new BigDecimal("4000"));
        booking.setSeatCharge(new BigDecimal("250"));
        booking.setBaggageCharge(new BigDecimal("100"));
        booking.setMealCharge(new BigDecimal("0"));
        booking.setTaxes(new BigDecimal("720"));
        booking.setTotalFare(new BigDecimal("5070"));

        when(bookingRepository.findByUserIdOrderByBookedAtDesc(99L)).thenReturn(List.of(booking));

        List<BookingResponse> responses = bookingService.getBookingsByUser(99L);
        assertEquals(1, responses.size());
        assertEquals("bkg-user-1", responses.get(0).getBookingId());
        assertEquals(List.of(11L, 12L), responses.get(0).getSeatIds());
    }

    @Test
    void getAllBookings_shouldReturnAllMappedResponses() {
        Booking booking = new Booking();
        booking.setBookingId("bkg-all-1");
        booking.setUserId(88L);
        booking.setFlightId(33L);
        booking.setSelectedSeatIds("19");
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPnrCode("PNR999");
        booking.setTripType(TripType.ONE_WAY);
        booking.setBaseFare(new BigDecimal("3200"));
        booking.setSeatCharge(new BigDecimal("0"));
        booking.setBaggageCharge(new BigDecimal("0"));
        booking.setMealCharge(new BigDecimal("0"));
        booking.setTaxes(new BigDecimal("576"));
        booking.setTotalFare(new BigDecimal("3776"));

        when(bookingRepository.findAll()).thenReturn(List.of(booking));

        List<BookingResponse> responses = bookingService.getAllBookings();
        assertEquals(1, responses.size());
        assertEquals("PNR999", responses.get(0).getPnrCode());
    }

    @Test
    void getBookingByPnr_shouldThrowWhenMissing() {
        when(bookingRepository.findByPnrCode("PNR-MISSING")).thenReturn(java.util.Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bookingService.getBookingByPnr("PNR-MISSING"));
    }

    @Test
    void cancelBooking_shouldRejectCompletedBooking() {
        Booking booking = new Booking();
        booking.setBookingId("bkg-completed");
        booking.setStatus(BookingStatus.COMPLETED);
        when(bookingRepository.findById("bkg-completed")).thenReturn(java.util.Optional.of(booking));

        CancelBookingRequest request = new CancelBookingRequest();
        request.setBookingId("bkg-completed");

        assertThrows(BadRequestException.class, () -> bookingService.cancelBooking(request));
    }

    @Test
    void updateBookingFare_shouldRejectWhenBookingCancelled() {
        Booking booking = new Booking();
        booking.setBookingId("bkg-cxl");
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById("bkg-cxl")).thenReturn(java.util.Optional.of(booking));

        assertThrows(BadRequestException.class, () -> bookingService.updateBookingFare("bkg-cxl", new UpdateBookingFareRequest()));
    }

    @Test
    void generateTicketPdf_shouldReturnNonEmptyBytesForExistingBooking() {
        Booking booking = new Booking();
        booking.setBookingId("bkg-pdf");
        booking.setUserId(7L);
        booking.setFlightId(51L);
        booking.setSelectedSeatIds("20,21");
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPnrCode("PNRPDF");
        booking.setTripType(TripType.ONE_WAY);
        booking.setTotalFare(new BigDecimal("6400"));
        booking.setBookedAt(LocalDateTime.now());

        when(bookingRepository.findById("bkg-pdf")).thenReturn(java.util.Optional.of(booking));

        byte[] pdf = bookingService.generateTicketPdf("bkg-pdf");

        assertNotNull(pdf);
        assertFalse(pdf.length == 0);
    }

    @Test
    void generateTicketQr_shouldReturnNonEmptyBytesForExistingBooking() {
        Booking booking = new Booking();
        booking.setBookingId("bkg-qr");
        booking.setUserId(7L);
        booking.setFlightId(51L);
        booking.setSelectedSeatIds("20");
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPnrCode("PNRQR1");
        booking.setTripType(TripType.ONE_WAY);
        booking.setTotalFare(new BigDecimal("6400"));
        booking.setBookedAt(LocalDateTime.now());

        when(bookingRepository.findById("bkg-qr")).thenReturn(java.util.Optional.of(booking));

        byte[] qr = bookingService.generateTicketQr("bkg-qr");

        assertNotNull(qr);
        assertFalse(qr.length == 0);
    }

    @Test
    void createBooking_shouldThrowWhenFlightMissing() {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setUserId(101L);
        request.setFlightId(44L);
        request.setTripType(TripType.ONE_WAY);
        request.setBaseFare(new BigDecimal("4500"));
        request.setSeatIds(List.of(11L));
        request.setLuggageKg(0);
        request.setContactEmail("u@example.com");
        request.setContactPhone("9999999999");

        when(seatServiceClient.getSeatMap(44L)).thenReturn(heldSeatMap(44L, List.of(11L)));
        when(flightServiceClient.getFlightById(44L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(request));
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    private SeatMapResponse heldSeatMap(Long flightId, List<Long> seatIds) {
        SeatMapResponse response = new SeatMapResponse();
        response.setFlightId(flightId);
        response.setSeats(seatIds.stream().map(id -> {
            SeatMapResponse.SeatView seatView = new SeatMapResponse.SeatView();
            seatView.setSeatId(id);
            seatView.setStatus("HELD");
            return seatView;
        }).toList());
        return response;
    }

    private SeatMapResponse availableSeatMap(Long flightId, Long seatId) {
        SeatMapResponse response = new SeatMapResponse();
        response.setFlightId(flightId);
        SeatMapResponse.SeatView seatView = new SeatMapResponse.SeatView();
        seatView.setSeatId(seatId);
        seatView.setStatus("AVAILABLE");
        response.setSeats(List.of(seatView));
        return response;
    }

    private PaymentResponse paymentResponse(String paymentId) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(paymentId);
        response.setStatus("PENDING");
        return response;
    }
}
