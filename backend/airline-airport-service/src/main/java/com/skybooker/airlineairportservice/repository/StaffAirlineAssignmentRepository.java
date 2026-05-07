package com.skybooker.airlineairportservice.repository;

import com.skybooker.airlineairportservice.entity.StaffAirlineAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffAirlineAssignmentRepository extends JpaRepository<StaffAirlineAssignment, Long> {

    Optional<StaffAirlineAssignment> findByUserId(Long userId);
}
