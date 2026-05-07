package com.skybooker.passengerservice.service.impl;

import com.skybooker.passengerservice.dto.AssignSeatRequest;
import com.skybooker.passengerservice.dto.PassengerRequest;
import com.skybooker.passengerservice.dto.PassengerResponse;
import com.skybooker.passengerservice.entity.PassengerInfo;
import com.skybooker.passengerservice.entity.PassengerType;
import com.skybooker.passengerservice.exception.BadRequestException;
import com.skybooker.passengerservice.exception.ResourceNotFoundException;
import com.skybooker.passengerservice.integration.BookingServiceClient;
import com.skybooker.passengerservice.integration.dto.BookingResponse;
import com.skybooker.passengerservice.repository.PassengerRepository;
import com.skybooker.passengerservice.service.PassengerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Service
public class PassengerServiceImpl implements PassengerService {

    private static final String TICKET_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final PassengerRepository passengerRepository;
    private final BookingServiceClient bookingServiceClient;
    private final SecureRandom random = new SecureRandom();

    public PassengerServiceImpl(PassengerRepository passengerRepository,
                                BookingServiceClient bookingServiceClient) {
        this.passengerRepository = passengerRepository;
        this.bookingServiceClient = bookingServiceClient;
    }

    @Override
    @Transactional
    public PassengerResponse addPassenger(PassengerRequest request) {
        validatePassengerData(request);
        BookingResponse booking = bookingServiceClient.getBookingById(request.getBookingId());
        validatePassengerCapacity(request.getBookingId(), booking.getSeatIds());
        passengerRepository.findByPassportNumber(request.getPassportNumber())
            .ifPresent(existing -> {
                throw new BadRequestException("Passport number already exists");
            });

        PassengerInfo passenger = new PassengerInfo();
        mapRequest(passenger, request);
        passenger.setTicketNumber(generateTicketNumber());
        return toResponse(passengerRepository.save(passenger));
    }

    @Override
    @Transactional(readOnly = true)
    public PassengerResponse getPassengerById(Long passengerId) {
        return toResponse(findPassenger(passengerId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PassengerResponse> getPassengersByBooking(String bookingId) {
        return passengerRepository.findByBookingIdOrderByPassengerIdAsc(bookingId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PassengerResponse getByPassportNumber(String passportNumber) {
        PassengerInfo passenger = passengerRepository.findByPassportNumber(passportNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Passenger not found for passport: " + passportNumber));
        return toResponse(passenger);
    }

    @Override
    @Transactional
    public PassengerResponse updatePassenger(Long passengerId, PassengerRequest request) {
        validatePassengerData(request);
        PassengerInfo passenger = findPassenger(passengerId);
        BookingResponse booking = bookingServiceClient.getBookingById(request.getBookingId());
        validatePassengerCapacityForUpdate(passenger, request, booking.getSeatIds());

        passengerRepository.findByPassportNumber(request.getPassportNumber())
            .filter(existing -> !existing.getPassengerId().equals(passengerId))
            .ifPresent(existing -> {
                throw new BadRequestException("Passport number already exists");
            });

        mapRequest(passenger, request);
        return toResponse(passengerRepository.save(passenger));
    }

    @Override
    @Transactional
    public PassengerResponse assignSeat(Long passengerId, AssignSeatRequest request) {
        PassengerInfo passenger = findPassenger(passengerId);
        BookingResponse booking = bookingServiceClient.getBookingById(passenger.getBookingId());

        if (booking.getSeatIds() == null || !booking.getSeatIds().contains(request.getSeatId())) {
            throw new BadRequestException("Seat does not belong to booking " + passenger.getBookingId());
        }

        if (!request.getSeatId().equals(passenger.getSeatId())
            && passengerRepository.existsByBookingIdAndSeatId(passenger.getBookingId(), request.getSeatId())) {
            throw new BadRequestException("Seat is already assigned to another passenger in this booking");
        }

        long passengerCount = passengerRepository.countByBookingId(passenger.getBookingId());
        if (passengerCount != booking.getSeatIds().size()) {
            throw new BadRequestException("Passenger count must match selected seats before seat assignment");
        }

        passenger.setSeatId(request.getSeatId());
        passenger.setSeatNumber(request.getSeatNumber());
        return toResponse(passengerRepository.save(passenger));
    }

    @Override
    @Transactional
    public void deletePassenger(Long passengerId) {
        PassengerInfo passenger = findPassenger(passengerId);
        passengerRepository.delete(passenger);
    }

    @Override
    @Transactional
    public void deletePassengersByBooking(String bookingId) {
        passengerRepository.deleteByBookingId(bookingId);
    }

    @Override
    @Transactional
    public void clearSeatAssignmentsByBooking(String bookingId) {
        List<PassengerInfo> passengers = passengerRepository.findByBookingIdOrderByPassengerIdAsc(bookingId);
        if (passengers.isEmpty()) {
            return;
        }

        for (PassengerInfo passenger : passengers) {
            passenger.setSeatId(null);
            passenger.setSeatNumber(null);
        }

        passengerRepository.saveAll(passengers);
    }

    @Override
    @Transactional(readOnly = true)
    public long getPassengerCount(String bookingId) {
        return passengerRepository.countByBookingId(bookingId);
    }

    private PassengerInfo findPassenger(Long passengerId) {
        return passengerRepository.findById(passengerId)
            .orElseThrow(() -> new ResourceNotFoundException("Passenger not found: " + passengerId));
    }

    private void validatePassengerData(PassengerRequest request) {
        if (request.getPassportExpiry().isBefore(LocalDate.now())) {
            throw new BadRequestException("Passport has already expired");
        }

        int age = Period.between(request.getDateOfBirth(), LocalDate.now()).getYears();
        if (request.getPassengerType() == PassengerType.INFANT && age >= 2) {
            throw new BadRequestException("Infant age must be below 2 years");
        }
        if (request.getPassengerType() == PassengerType.CHILD && (age < 2 || age >= 12)) {
            throw new BadRequestException("Child age must be between 2 and 11 years");
        }
        if (request.getPassengerType() == PassengerType.ADULT && age < 12) {
            throw new BadRequestException("Adult age must be at least 12 years");
        }
    }

    private void validatePassengerCapacity(String bookingId, List<Long> bookingSeatIds) {
        if (bookingSeatIds == null || bookingSeatIds.isEmpty()) {
            throw new BadRequestException("Booking has no selected seats");
        }

        long existingPassengers = passengerRepository.countByBookingId(bookingId);
        if (existingPassengers >= bookingSeatIds.size()) {
            throw new BadRequestException("Passenger count cannot exceed selected seats for booking");
        }
    }

    private void validatePassengerCapacityForUpdate(PassengerInfo existingPassenger,
                                                    PassengerRequest request,
                                                    List<Long> bookingSeatIds) {
        if (bookingSeatIds == null || bookingSeatIds.isEmpty()) {
            throw new BadRequestException("Booking has no selected seats");
        }

        if (!existingPassenger.getBookingId().equals(request.getBookingId())) {
            long existingPassengers = passengerRepository.countByBookingId(request.getBookingId());
            if (existingPassengers >= bookingSeatIds.size()) {
                throw new BadRequestException("Passenger count cannot exceed selected seats for booking");
            }
            existingPassenger.setSeatId(null);
            existingPassenger.setSeatNumber(null);
        }
    }

    private String generateTicketNumber() {
        for (int attempt = 0; attempt < 50; attempt++) {
            StringBuilder ticket = new StringBuilder("TK");
            for (int i = 0; i < 8; i++) {
                ticket.append(TICKET_CHARS.charAt(random.nextInt(TICKET_CHARS.length())));
            }
            String candidate = ticket.toString();
            if (passengerRepository.findByTicketNumber(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new BadRequestException("Unable to generate unique ticket number");
    }

    private void mapRequest(PassengerInfo passenger, PassengerRequest request) {
        passenger.setBookingId(request.getBookingId());
        passenger.setTitle(request.getTitle());
        passenger.setFirstName(request.getFirstName());
        passenger.setLastName(request.getLastName());
        passenger.setDateOfBirth(request.getDateOfBirth());
        passenger.setGender(request.getGender());
        passenger.setPassengerType(request.getPassengerType());
        passenger.setPassportNumber(request.getPassportNumber());
        passenger.setNationality(request.getNationality());
        passenger.setPassportExpiry(request.getPassportExpiry());
        passenger.setMealPreference(request.getMealPreference());
        passenger.setExtraBaggageKg(request.getExtraBaggageKg() == null ? 0 : Math.max(0, request.getExtraBaggageKg()));
    }

    private PassengerResponse toResponse(PassengerInfo passenger) {
        PassengerResponse response = new PassengerResponse();
        response.setPassengerId(passenger.getPassengerId());
        response.setBookingId(passenger.getBookingId());
        response.setTitle(passenger.getTitle());
        response.setFirstName(passenger.getFirstName());
        response.setLastName(passenger.getLastName());
        response.setDateOfBirth(passenger.getDateOfBirth());
        response.setGender(passenger.getGender());
        response.setPassengerType(passenger.getPassengerType());
        response.setPassportNumber(passenger.getPassportNumber());
        response.setNationality(passenger.getNationality());
        response.setPassportExpiry(passenger.getPassportExpiry());
        response.setMealPreference(passenger.getMealPreference());
        response.setExtraBaggageKg(passenger.getExtraBaggageKg());
        response.setSeatId(passenger.getSeatId());
        response.setSeatNumber(passenger.getSeatNumber());
        response.setTicketNumber(passenger.getTicketNumber());
        response.setCreatedAt(passenger.getCreatedAt());
        return response;
    }
}
