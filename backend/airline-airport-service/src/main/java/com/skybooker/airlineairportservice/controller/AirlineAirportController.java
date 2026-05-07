package com.skybooker.airlineairportservice.controller;

import com.skybooker.airlineairportservice.dto.StaffAirlineAssignmentRequest;
import com.skybooker.airlineairportservice.entity.Airline;
import com.skybooker.airlineairportservice.entity.Airport;
import com.skybooker.airlineairportservice.service.AirlineAirportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class AirlineAirportController {

    private final AirlineAirportService airlineAirportService;

    public AirlineAirportController(AirlineAirportService airlineAirportService) {
        this.airlineAirportService = airlineAirportService;
    }

    @GetMapping("/airports/search")
    public List<Airport> searchAirports(@RequestParam("query") String query) {
        return airlineAirportService.searchAirports(query);
    }

    @GetMapping("/airlines")
    public List<Airline> getAllAirlines() {
        return airlineAirportService.getAllAirlines();
    }

    @GetMapping("/airlines/{airlineId}")
    public Airline getAirlineById(@PathVariable Long airlineId) {
        return airlineAirportService.getAirlineById(airlineId);
    }

    @PostMapping("/airlines")
    public Airline createAirline(@Valid @RequestBody Airline airline) {
        return airlineAirportService.createAirline(airline);
    }

    @PutMapping("/airlines/{airlineId}")
    public Airline updateAirline(@PathVariable Long airlineId, @Valid @RequestBody Airline airline) {
        return airlineAirportService.updateAirline(airlineId, airline);
    }

    @DeleteMapping("/airlines/{airlineId}")
    public ResponseEntity<Void> deleteAirline(@PathVariable Long airlineId) {
        airlineAirportService.deleteAirline(airlineId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/airports")
    public List<Airport> getAllAirports() {
        return airlineAirportService.getAllAirports();
    }

    @PostMapping("/airports")
    public Airport createAirport(@Valid @RequestBody Airport airport) {
        return airlineAirportService.createAirport(airport);
    }

    @PutMapping("/airports/{airportId}")
    public Airport updateAirport(@PathVariable Long airportId, @Valid @RequestBody Airport airport) {
        return airlineAirportService.updateAirport(airportId, airport);
    }

    @DeleteMapping("/airports/{airportId}")
    public ResponseEntity<Void> deleteAirport(@PathVariable Long airportId) {
        airlineAirportService.deleteAirport(airportId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/staff-airlines/{userId}")
    public Map<String, Long> assignStaffAirline(@PathVariable Long userId,
                                                @Valid @RequestBody StaffAirlineAssignmentRequest request) {
        Long airlineId = airlineAirportService.assignStaffToAirline(userId, request.getAirlineId());
        return Map.of("userId", userId, "airlineId", airlineId);
    }

    @GetMapping("/staff-airlines/{userId}")
    public Map<String, Long> getStaffAirline(@PathVariable Long userId) {
        Long airlineId = airlineAirportService.getAssignedAirlineId(userId);
        return Map.of("userId", userId, "airlineId", airlineId);
    }
}
