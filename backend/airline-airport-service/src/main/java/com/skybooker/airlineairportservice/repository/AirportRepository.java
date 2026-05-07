package com.skybooker.airlineairportservice.repository;

import com.skybooker.airlineairportservice.entity.Airport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AirportRepository extends JpaRepository<Airport, Long> {

    List<Airport> findTop10ByNameContainingIgnoreCaseOrCityContainingIgnoreCaseOrIataCodeContainingIgnoreCaseOrderByNameAsc(String name, String city, String iataCode);

    Optional<Airport> findByIataCodeIgnoreCase(String iataCode);
}
