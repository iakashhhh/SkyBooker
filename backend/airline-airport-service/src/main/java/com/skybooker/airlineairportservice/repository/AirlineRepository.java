package com.skybooker.airlineairportservice.repository;

import com.skybooker.airlineairportservice.entity.Airline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AirlineRepository extends JpaRepository<Airline, Long> {

	Optional<Airline> findByIataCodeIgnoreCase(String iataCode);
}
