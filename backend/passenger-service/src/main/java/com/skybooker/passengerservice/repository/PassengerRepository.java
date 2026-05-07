package com.skybooker.passengerservice.repository;

import com.skybooker.passengerservice.entity.PassengerInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PassengerRepository extends JpaRepository<PassengerInfo, Long> {

    List<PassengerInfo> findByBookingIdOrderByPassengerIdAsc(String bookingId);

    Optional<PassengerInfo> findByPassportNumber(String passportNumber);

    Optional<PassengerInfo> findByTicketNumber(String ticketNumber);

    List<PassengerInfo> findBySeatId(Long seatId);

    boolean existsByBookingIdAndSeatId(String bookingId, Long seatId);

    long countByBookingId(String bookingId);

    void deleteByBookingId(String bookingId);
}
