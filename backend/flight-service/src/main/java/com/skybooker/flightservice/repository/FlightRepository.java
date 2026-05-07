package com.skybooker.flightservice.repository;

import com.skybooker.flightservice.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * This repository handles persistence operations for flight records.
 * JpaSpecificationExecutor is used for scalable dynamic search filters.
 */
public interface FlightRepository extends JpaRepository<Flight, Long>, JpaSpecificationExecutor<Flight> {

    /**
     * Finds flight by unique flight number.
     */
    Optional<Flight> findByFlightNumber(String flightNumber);

    /**
     * Checks duplicate flight number at create time.
     */
    boolean existsByFlightNumber(String flightNumber);

    Optional<Flight> findTopByOrderByDepartureTimeDesc();

    long countByDepartureTimeBetween(LocalDateTime from, LocalDateTime to);

    long countByAirlineId(Long airlineId);

    List<Flight> findByAirlineIdOrderByDepartureTimeDesc(Long airlineId);

    @Transactional
    long deleteByDepartureTimeBefore(LocalDateTime departureTime);

    @Transactional
    long deleteByDepartureTimeAfter(LocalDateTime departureTime);

    List<Flight> findByDepartureTimeBetween(LocalDateTime from, LocalDateTime to);

    boolean existsByAirlineIdAndOriginAirportCodeAndDestinationAirportCodeAndDepartureTime(
        Long airlineId,
        String originAirportCode,
        String destinationAirportCode,
        LocalDateTime departureTime
    );
}
