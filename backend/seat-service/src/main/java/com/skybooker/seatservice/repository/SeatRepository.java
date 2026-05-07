package com.skybooker.seatservice.repository;

import com.skybooker.seatservice.entity.Seat;
import com.skybooker.seatservice.entity.SeatClass;
import com.skybooker.seatservice.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for seat inventory operations.
 */
public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByFlightIdOrderByRowAscColumnAsc(Long flightId);

    List<Seat> findByFlightIdAndStatus(Long flightId, SeatStatus status);

    List<Seat> findBySeatIdInAndFlightId(List<Long> seatIds, Long flightId);

    List<Seat> findByStatusAndHoldExpiresAtBefore(SeatStatus status, LocalDateTime holdExpiresAt);

    @Query("""
            SELECT s
            FROM Seat s
            WHERE s.flightId = :flightId
              AND s.status = :status
              AND s.holdExpiresAt IS NOT NULL
              AND s.holdExpiresAt <= :now
            """)
    List<Seat> findExpiredHoldsByFlightId(@Param("flightId") Long flightId,
                                          @Param("status") SeatStatus status,
                                          @Param("now") LocalDateTime now);

    @Query("""
            SELECT s.seatClass AS seatClass, COUNT(s) AS count
            FROM Seat s
            WHERE s.flightId = :flightId
              AND s.status = :status
            GROUP BY s.seatClass
            """)
    List<SeatAvailabilityProjection> countByFlightAndStatusGroupedByClass(@Param("flightId") Long flightId,
                                                                          @Param("status") SeatStatus status);

    interface SeatAvailabilityProjection {
        SeatClass getSeatClass();

        Long getCount();
    }
}
