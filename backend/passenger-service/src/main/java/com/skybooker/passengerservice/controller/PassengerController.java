package com.skybooker.passengerservice.controller;

import com.skybooker.passengerservice.dto.AssignSeatRequest;
import com.skybooker.passengerservice.dto.PassengerRequest;
import com.skybooker.passengerservice.dto.PassengerResponse;
import com.skybooker.passengerservice.service.PassengerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/passengers", "/passengers"})
public class PassengerController {

    private final PassengerService passengerService;

    public PassengerController(PassengerService passengerService) {
        this.passengerService = passengerService;
    }

    @PostMapping({"", "/add"})
    public PassengerResponse addPassenger(@Valid @RequestBody PassengerRequest request) {
        return passengerService.addPassenger(request);
    }

    @GetMapping("/{passengerId}")
    public PassengerResponse getPassenger(@PathVariable Long passengerId) {
        return passengerService.getPassengerById(passengerId);
    }

    @GetMapping("/booking/{bookingId}")
    public List<PassengerResponse> getPassengersByBooking(@PathVariable String bookingId) {
        return passengerService.getPassengersByBooking(bookingId);
    }

    @GetMapping("/passport/{passportNumber}")
    public PassengerResponse getByPassport(@PathVariable String passportNumber) {
        return passengerService.getByPassportNumber(passportNumber);
    }

    @PutMapping("/{passengerId}")
    public PassengerResponse updatePassenger(@PathVariable Long passengerId,
                                             @Valid @RequestBody PassengerRequest request) {
        return passengerService.updatePassenger(passengerId, request);
    }

    @PutMapping("/update")
    public PassengerResponse updatePassengerViaStrictPath(@org.springframework.web.bind.annotation.RequestParam("passengerId") Long passengerId,
                                                          @Valid @RequestBody PassengerRequest request) {
        return passengerService.updatePassenger(passengerId, request);
    }

    @PutMapping("/{passengerId}/assign-seat")
    public PassengerResponse assignSeat(@PathVariable Long passengerId,
                                        @Valid @RequestBody AssignSeatRequest request) {
        return passengerService.assignSeat(passengerId, request);
    }

    @DeleteMapping("/{passengerId}")
    public ResponseEntity<Void> deletePassenger(@PathVariable Long passengerId) {
        passengerService.deletePassenger(passengerId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/booking/{bookingId}")
    public ResponseEntity<Void> deleteByBooking(@PathVariable String bookingId) {
        passengerService.deletePassengersByBooking(bookingId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/booking/{bookingId}/clear-seats")
    public ResponseEntity<Void> clearSeatAssignments(@PathVariable String bookingId) {
        passengerService.clearSeatAssignmentsByBooking(bookingId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/count/{bookingId}")
    public Map<String, Object> getCountByBooking(@PathVariable String bookingId) {
        return Map.of("bookingId", bookingId, "count", passengerService.getPassengerCount(bookingId));
    }
}
