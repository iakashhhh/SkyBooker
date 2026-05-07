package com.skybooker.passengerservice.service;

import com.skybooker.passengerservice.dto.AssignSeatRequest;
import com.skybooker.passengerservice.dto.PassengerRequest;
import com.skybooker.passengerservice.dto.PassengerResponse;

import java.util.List;

public interface PassengerService {

    PassengerResponse addPassenger(PassengerRequest request);

    PassengerResponse getPassengerById(Long passengerId);

    List<PassengerResponse> getPassengersByBooking(String bookingId);

    PassengerResponse getByPassportNumber(String passportNumber);

    PassengerResponse updatePassenger(Long passengerId, PassengerRequest request);

    PassengerResponse assignSeat(Long passengerId, AssignSeatRequest request);

    void deletePassenger(Long passengerId);

    void deletePassengersByBooking(String bookingId);

    void clearSeatAssignmentsByBooking(String bookingId);

    long getPassengerCount(String bookingId);
}
