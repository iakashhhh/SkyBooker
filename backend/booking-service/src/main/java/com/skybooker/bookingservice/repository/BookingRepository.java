package com.skybooker.bookingservice.repository;

import com.skybooker.bookingservice.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for booking persistence operations.
 */
public interface BookingRepository extends JpaRepository<Booking, String> {

    List<Booking> findByUserIdOrderByBookedAtDesc(Long userId);

    Optional<Booking> findByPnrCode(String pnrCode);

    boolean existsByPnrCode(String pnrCode);
}
