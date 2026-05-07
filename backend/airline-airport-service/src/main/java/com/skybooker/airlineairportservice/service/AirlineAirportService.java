package com.skybooker.airlineairportservice.service;

import com.skybooker.airlineairportservice.entity.Airline;
import com.skybooker.airlineairportservice.entity.Airport;

import java.util.List;

public interface AirlineAirportService {

    List<Airport> searchAirports(String query);

    List<Airline> getAllAirlines();

    Airline getAirlineById(Long airlineId);

    Airline createAirline(Airline airline);

    Airline updateAirline(Long airlineId, Airline airline);

    void deleteAirline(Long airlineId);

    List<Airport> getAllAirports();

    Airport createAirport(Airport airport);

    Airport updateAirport(Long airportId, Airport airport);

    void deleteAirport(Long airportId);

    Long assignStaffToAirline(Long userId, Long airlineId);

    Long getAssignedAirlineId(Long userId);
}
