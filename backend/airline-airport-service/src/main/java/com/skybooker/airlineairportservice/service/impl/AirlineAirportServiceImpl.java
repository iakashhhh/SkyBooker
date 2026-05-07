package com.skybooker.airlineairportservice.service.impl;

import com.skybooker.airlineairportservice.entity.Airline;
import com.skybooker.airlineairportservice.entity.Airport;
import com.skybooker.airlineairportservice.entity.StaffAirlineAssignment;
import com.skybooker.airlineairportservice.exception.ResourceNotFoundException;
import com.skybooker.airlineairportservice.repository.AirlineRepository;
import com.skybooker.airlineairportservice.repository.AirportRepository;
import com.skybooker.airlineairportservice.repository.StaffAirlineAssignmentRepository;
import com.skybooker.airlineairportservice.service.AirlineAirportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AirlineAirportServiceImpl implements AirlineAirportService {

    private final AirlineRepository airlineRepository;
    private final AirportRepository airportRepository;
    private final StaffAirlineAssignmentRepository staffAirlineAssignmentRepository;

    public AirlineAirportServiceImpl(AirlineRepository airlineRepository,
                                     AirportRepository airportRepository,
                                     StaffAirlineAssignmentRepository staffAirlineAssignmentRepository) {
        this.airlineRepository = airlineRepository;
        this.airportRepository = airportRepository;
        this.staffAirlineAssignmentRepository = staffAirlineAssignmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Airport> searchAirports(String query) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isBlank()) {
            return List.of();
        }
        return airportRepository.findTop10ByNameContainingIgnoreCaseOrCityContainingIgnoreCaseOrIataCodeContainingIgnoreCaseOrderByNameAsc(
            safeQuery,
            safeQuery,
            safeQuery
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Airline> getAllAirlines() {
        return airlineRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Airline getAirlineById(Long airlineId) {
        return airlineRepository.findById(airlineId)
            .orElseThrow(() -> new ResourceNotFoundException("Airline not found: " + airlineId));
    }

    @Override
    @Transactional
    public Airline createAirline(Airline airline) {
        return airlineRepository.save(airline);
    }

    @Override
    @Transactional
    public Airline updateAirline(Long airlineId, Airline airline) {
        Airline existing = airlineRepository.findById(airlineId)
            .orElseThrow(() -> new ResourceNotFoundException("Airline not found: " + airlineId));
        existing.setName(airline.getName());
        existing.setIataCode(airline.getIataCode());
        existing.setCountry(airline.getCountry());
        existing.setActive(airline.isActive());
        return airlineRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteAirline(Long airlineId) {
        if (!airlineRepository.existsById(airlineId)) {
            throw new ResourceNotFoundException("Airline not found: " + airlineId);
        }
        airlineRepository.deleteById(airlineId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Airport> getAllAirports() {
        return airportRepository.findAll();
    }

    @Override
    @Transactional
    public Airport createAirport(Airport airport) {
        return airportRepository.save(airport);
    }

    @Override
    @Transactional
    public Airport updateAirport(Long airportId, Airport airport) {
        Airport existing = airportRepository.findById(airportId)
            .orElseThrow(() -> new ResourceNotFoundException("Airport not found: " + airportId));
        existing.setName(airport.getName());
        existing.setIataCode(airport.getIataCode());
        existing.setCity(airport.getCity());
        existing.setCountry(airport.getCountry());
        existing.setTimezone(airport.getTimezone());
        existing.setLatitude(airport.getLatitude());
        existing.setLongitude(airport.getLongitude());
        return airportRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteAirport(Long airportId) {
        if (!airportRepository.existsById(airportId)) {
            throw new ResourceNotFoundException("Airport not found: " + airportId);
        }
        airportRepository.deleteById(airportId);
    }

    @Override
    @Transactional
    public Long assignStaffToAirline(Long userId, Long airlineId) {
        if (!airlineRepository.existsById(airlineId)) {
            throw new ResourceNotFoundException("Airline not found: " + airlineId);
        }

        StaffAirlineAssignment assignment = staffAirlineAssignmentRepository.findByUserId(userId)
            .orElseGet(StaffAirlineAssignment::new);
        assignment.setUserId(userId);
        assignment.setAirlineId(airlineId);
        return staffAirlineAssignmentRepository.save(assignment).getAirlineId();
    }

    @Override
    @Transactional(readOnly = true)
    public Long getAssignedAirlineId(Long userId) {
        return staffAirlineAssignmentRepository.findByUserId(userId)
            .map(StaffAirlineAssignment::getAirlineId)
            .orElseThrow(() -> new ResourceNotFoundException("No airline assignment found for user: " + userId));
    }
}
