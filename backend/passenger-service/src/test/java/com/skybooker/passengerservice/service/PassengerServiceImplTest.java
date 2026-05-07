package com.skybooker.passengerservice.service;

import com.skybooker.passengerservice.dto.AssignSeatRequest;
import com.skybooker.passengerservice.dto.PassengerRequest;
import com.skybooker.passengerservice.entity.PassengerInfo;
import com.skybooker.passengerservice.entity.PassengerType;
import com.skybooker.passengerservice.exception.BadRequestException;
import com.skybooker.passengerservice.exception.ResourceNotFoundException;
import com.skybooker.passengerservice.integration.BookingServiceClient;
import com.skybooker.passengerservice.integration.dto.BookingResponse;
import com.skybooker.passengerservice.repository.PassengerRepository;
import com.skybooker.passengerservice.service.impl.PassengerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerServiceImplTest {

    @Mock
    private PassengerRepository passengerRepository;

    @Mock
    private BookingServiceClient bookingServiceClient;

    private PassengerServiceImpl passengerService;

    @BeforeEach
    void setUp() {
        passengerService = new PassengerServiceImpl(passengerRepository, bookingServiceClient);
    }

    @Test
    void addPassenger_shouldCreatePassengerWhenDataIsValid() {
        PassengerRequest request = baseRequest();
        when(bookingServiceClient.getBookingById("BKG-100")).thenReturn(booking("BKG-100", List.of(11L, 12L)));
        when(passengerRepository.countByBookingId("BKG-100")).thenReturn(0L);
        when(passengerRepository.findByPassportNumber("P123456")).thenReturn(Optional.empty());
        when(passengerRepository.findByTicketNumber(anyString())).thenReturn(Optional.empty());
        when(passengerRepository.save(any(PassengerInfo.class))).thenAnswer(invocation -> {
            PassengerInfo saved = invocation.getArgument(0);
            saved.setPassengerId(77L);
            return saved;
        });

        var response = passengerService.addPassenger(request);

        assertEquals(77L, response.getPassengerId());
        assertEquals("BKG-100", response.getBookingId());
        assertEquals("P123456", response.getPassportNumber());
        assertNotNull(response.getTicketNumber());
    }

    @Test
    void addPassenger_shouldRejectExpiredPassport() {
        PassengerRequest request = baseRequest();
        request.setPassportExpiry(LocalDate.now().minusDays(1));

        assertThrows(BadRequestException.class, () -> passengerService.addPassenger(request));
    }

    @Test
    void addPassenger_shouldRejectBookingWithoutSeats() {
        PassengerRequest request = baseRequest();
        when(bookingServiceClient.getBookingById("BKG-100")).thenReturn(booking("BKG-100", List.of()));

        assertThrows(BadRequestException.class, () -> passengerService.addPassenger(request));
    }

    @Test
    void addPassenger_shouldRejectWhenCapacityAlreadyReached() {
        PassengerRequest request = baseRequest();
        when(bookingServiceClient.getBookingById("BKG-100")).thenReturn(booking("BKG-100", List.of(11L, 12L)));
        when(passengerRepository.countByBookingId("BKG-100")).thenReturn(2L);

        assertThrows(BadRequestException.class, () -> passengerService.addPassenger(request));
    }

    @Test
    void addPassenger_shouldRejectDuplicatePassport() {
        PassengerRequest request = baseRequest();
        when(bookingServiceClient.getBookingById("BKG-100")).thenReturn(booking("BKG-100", List.of(11L, 12L)));
        when(passengerRepository.countByBookingId("BKG-100")).thenReturn(0L);
        when(passengerRepository.findByPassportNumber("P123456")).thenReturn(Optional.of(new PassengerInfo()));

        assertThrows(BadRequestException.class, () -> passengerService.addPassenger(request));
    }

    @Test
    void updatePassenger_shouldUpdateAndResetSeatWhenBookingChanges() {
        PassengerInfo existing = existingPassenger(1L, "OLD-BKG", "PASS1", PassengerType.ADULT);
        existing.setSeatId(99L);
        existing.setSeatNumber("12A");

        PassengerRequest request = baseRequest();
        request.setBookingId("NEW-BKG");
        request.setPassportNumber("PASS2");

        when(passengerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookingServiceClient.getBookingById("NEW-BKG")).thenReturn(booking("NEW-BKG", List.of(21L, 22L)));
        when(passengerRepository.countByBookingId("NEW-BKG")).thenReturn(0L);
        when(passengerRepository.findByPassportNumber("PASS2")).thenReturn(Optional.empty());
        when(passengerRepository.save(any(PassengerInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = passengerService.updatePassenger(1L, request);

        assertEquals("NEW-BKG", response.getBookingId());
        assertNull(response.getSeatId());
        assertNull(response.getSeatNumber());
    }

    @Test
    void updatePassenger_shouldRejectWhenOtherPassengerUsesPassport() {
        PassengerInfo existing = existingPassenger(1L, "BKG-100", "PASS1", PassengerType.ADULT);
        PassengerInfo another = existingPassenger(2L, "BKG-100", "PASS2", PassengerType.ADULT);

        PassengerRequest request = baseRequest();
        request.setPassportNumber("PASS2");

        when(passengerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookingServiceClient.getBookingById("BKG-100")).thenReturn(booking("BKG-100", List.of(11L, 12L)));
        when(passengerRepository.findByPassportNumber("PASS2")).thenReturn(Optional.of(another));

        assertThrows(BadRequestException.class, () -> passengerService.updatePassenger(1L, request));
    }

    @Test
    void assignSeat_shouldRejectWhenSeatIsNotPartOfBooking() {
        PassengerInfo existing = existingPassenger(1L, "BKG-100", "PASS1", PassengerType.ADULT);
        when(passengerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookingServiceClient.getBookingById("BKG-100")).thenReturn(booking("BKG-100", List.of(11L, 12L)));

        AssignSeatRequest request = new AssignSeatRequest();
        request.setSeatId(88L);
        request.setSeatNumber("14C");

        assertThrows(BadRequestException.class, () -> passengerService.assignSeat(1L, request));
    }

    @Test
    void assignSeat_shouldRejectWhenSeatAlreadyAssigned() {
        PassengerInfo existing = existingPassenger(1L, "BKG-100", "PASS1", PassengerType.ADULT);
        when(passengerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookingServiceClient.getBookingById("BKG-100")).thenReturn(booking("BKG-100", List.of(11L, 12L)));
        when(passengerRepository.existsByBookingIdAndSeatId("BKG-100", 11L)).thenReturn(true);

        AssignSeatRequest request = new AssignSeatRequest();
        request.setSeatId(11L);
        request.setSeatNumber("11A");

        assertThrows(BadRequestException.class, () -> passengerService.assignSeat(1L, request));
    }

    @Test
    void assignSeat_shouldRejectWhenPassengerCountDoesNotMatchSeats() {
        PassengerInfo existing = existingPassenger(1L, "BKG-100", "PASS1", PassengerType.ADULT);
        when(passengerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookingServiceClient.getBookingById("BKG-100")).thenReturn(booking("BKG-100", List.of(11L, 12L)));
        when(passengerRepository.existsByBookingIdAndSeatId("BKG-100", 11L)).thenReturn(false);
        when(passengerRepository.countByBookingId("BKG-100")).thenReturn(1L);

        AssignSeatRequest request = new AssignSeatRequest();
        request.setSeatId(11L);
        request.setSeatNumber("11A");

        assertThrows(BadRequestException.class, () -> passengerService.assignSeat(1L, request));
    }

    @Test
    void assignSeat_shouldUpdateSeatWhenRequestIsValid() {
        PassengerInfo existing = existingPassenger(1L, "BKG-100", "PASS1", PassengerType.ADULT);
        when(passengerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookingServiceClient.getBookingById("BKG-100")).thenReturn(booking("BKG-100", List.of(11L, 12L)));
        when(passengerRepository.existsByBookingIdAndSeatId("BKG-100", 11L)).thenReturn(false);
        when(passengerRepository.countByBookingId("BKG-100")).thenReturn(2L);
        when(passengerRepository.save(any(PassengerInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssignSeatRequest request = new AssignSeatRequest();
        request.setSeatId(11L);
        request.setSeatNumber("11A");

        var response = passengerService.assignSeat(1L, request);
        assertEquals(11L, response.getSeatId());
        assertEquals("11A", response.getSeatNumber());
    }

    @Test
    void clearSeatAssignmentsByBooking_shouldNoOpWhenNoPassengersExist() {
        when(passengerRepository.findByBookingIdOrderByPassengerIdAsc("BKG-100")).thenReturn(List.of());

        passengerService.clearSeatAssignmentsByBooking("BKG-100");

        verify(passengerRepository, never()).saveAll(anyList());
    }

    @Test
    void clearSeatAssignmentsByBooking_shouldClearSeatDataForAllPassengers() {
        PassengerInfo p1 = existingPassenger(1L, "BKG-100", "PASS1", PassengerType.ADULT);
        p1.setSeatId(11L);
        p1.setSeatNumber("11A");
        PassengerInfo p2 = existingPassenger(2L, "BKG-100", "PASS2", PassengerType.ADULT);
        p2.setSeatId(12L);
        p2.setSeatNumber("12A");

        when(passengerRepository.findByBookingIdOrderByPassengerIdAsc("BKG-100")).thenReturn(List.of(p1, p2));

        passengerService.clearSeatAssignmentsByBooking("BKG-100");

        ArgumentCaptor<List<PassengerInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(passengerRepository).saveAll(captor.capture());
        assertNull(captor.getValue().get(0).getSeatId());
        assertNull(captor.getValue().get(1).getSeatNumber());
    }

    @Test
    void getPassengerById_shouldThrowWhenPassengerMissing() {
        when(passengerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> passengerService.getPassengerById(99L));
    }

    @Test
    void getByPassportNumber_shouldThrowWhenPassportNotFound() {
        when(passengerRepository.findByPassportNumber("P-NONE")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> passengerService.getByPassportNumber("P-NONE"));
    }

    @Test
    void deletePassenger_shouldDeleteWhenPassengerExists() {
        PassengerInfo passenger = existingPassenger(5L, "BKG-100", "PASS5", PassengerType.ADULT);
        when(passengerRepository.findById(5L)).thenReturn(Optional.of(passenger));

        passengerService.deletePassenger(5L);

        verify(passengerRepository).delete(passenger);
    }

    @Test
    void addPassenger_shouldRejectInfantOlderThanAllowedRange() {
        PassengerRequest request = baseRequest();
        request.setPassengerType(PassengerType.INFANT);
        request.setDateOfBirth(LocalDate.now().minusYears(4));

        assertThrows(BadRequestException.class, () -> passengerService.addPassenger(request));
    }

    private PassengerRequest baseRequest() {
        PassengerRequest request = new PassengerRequest();
        request.setBookingId("BKG-100");
        request.setTitle("Mr");
        request.setFirstName("Ravi");
        request.setLastName("Shah");
        request.setDateOfBirth(LocalDate.now().minusYears(30));
        request.setGender("Male");
        request.setPassengerType(PassengerType.ADULT);
        request.setPassportNumber("P123456");
        request.setNationality("Indian");
        request.setPassportExpiry(LocalDate.now().plusYears(3));
        request.setExtraBaggageKg(5);
        return request;
    }

    private BookingResponse booking(String bookingId, List<Long> seatIds) {
        BookingResponse booking = new BookingResponse();
        booking.setBookingId(bookingId);
        booking.setSeatIds(seatIds);
        return booking;
    }

    private PassengerInfo existingPassenger(Long id, String bookingId, String passport, PassengerType type) {
        PassengerInfo passenger = new PassengerInfo();
        passenger.setPassengerId(id);
        passenger.setBookingId(bookingId);
        passenger.setTitle("Mr");
        passenger.setFirstName("Test");
        passenger.setLastName("User");
        passenger.setDateOfBirth(LocalDate.now().minusYears(28));
        passenger.setGender("Male");
        passenger.setPassengerType(type);
        passenger.setPassportNumber(passport);
        passenger.setNationality("Indian");
        passenger.setPassportExpiry(LocalDate.now().plusYears(4));
        passenger.setTicketNumber("TKABCD12");
        return passenger;
    }
}
