package com.skybooker.airlineairportservice.service.impl;

import com.skybooker.airlineairportservice.entity.Airline;
import com.skybooker.airlineairportservice.entity.Airport;
import com.skybooker.airlineairportservice.entity.StaffAirlineAssignment;
import com.skybooker.airlineairportservice.exception.ResourceNotFoundException;
import com.skybooker.airlineairportservice.repository.AirlineRepository;
import com.skybooker.airlineairportservice.repository.AirportRepository;
import com.skybooker.airlineairportservice.repository.StaffAirlineAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AirlineAirportServiceImplTest {

    @Mock
    private AirlineRepository airlineRepository;

    @Mock
    private AirportRepository airportRepository;

    @Mock
    private StaffAirlineAssignmentRepository staffAirlineAssignmentRepository;

    @InjectMocks
    private AirlineAirportServiceImpl service;

    @Test
    void searchAirportsShouldReturnEmptyWhenQueryIsBlank() {
        List<Airport> response = service.searchAirports("   ");

        assertEquals(0, response.size());
        verifyNoInteractions(airportRepository);
    }

    @Test
    void searchAirportsShouldReturnEmptyWhenQueryIsNull() {
        List<Airport> response = service.searchAirports(null);

        assertEquals(0, response.size());
        verifyNoInteractions(airportRepository);
    }

    @Test
    void searchAirportsShouldDelegateToRepositoryWithTrimmedQuery() {
        Airport airport = new Airport();
        airport.setAirportId(1L);
        airport.setName("Indira Gandhi International");
        when(airportRepository.findTop10ByNameContainingIgnoreCaseOrCityContainingIgnoreCaseOrIataCodeContainingIgnoreCaseOrderByNameAsc(
            "del", "del", "del")).thenReturn(List.of(airport));

        List<Airport> response = service.searchAirports("  del ");

        assertEquals(1, response.size());
        assertSame(airport, response.get(0));
    }

    @Test
    void updateAirlineShouldThrowWhenAirlineDoesNotExist() {
        when(airlineRepository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateAirline(7L, new Airline()));
        verify(airlineRepository, never()).save(any(Airline.class));
    }

    @Test
    void updateAirlineShouldApplyMutableFieldsAndSave() {
        Airline existing = new Airline();
        existing.setAirlineId(3L);
        existing.setName("Old Name");
        existing.setIataCode("OLD");
        existing.setCountry("Old Country");
        existing.setActive(false);

        Airline update = new Airline();
        update.setName("Sky India");
        update.setIataCode("SKY");
        update.setCountry("India");
        update.setActive(true);

        when(airlineRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(airlineRepository.save(existing)).thenReturn(existing);

        Airline saved = service.updateAirline(3L, update);

        assertEquals("Sky India", saved.getName());
        assertEquals("SKY", saved.getIataCode());
        assertEquals("India", saved.getCountry());
        assertEquals(true, saved.isActive());
    }

    @Test
    void getAllAirlinesShouldReturnRepositoryResult() {
        Airline airline = new Airline();
        airline.setAirlineId(5L);
        when(airlineRepository.findAll()).thenReturn(List.of(airline));

        List<Airline> response = service.getAllAirlines();

        assertEquals(1, response.size());
        assertSame(airline, response.get(0));
    }

    @Test
    void getAirlineByIdShouldReturnRecordWhenFound() {
        Airline airline = new Airline();
        airline.setAirlineId(9L);
        when(airlineRepository.findById(9L)).thenReturn(Optional.of(airline));

        Airline response = service.getAirlineById(9L);

        assertSame(airline, response);
    }

    @Test
    void getAirlineByIdShouldThrowWhenMissing() {
        when(airlineRepository.findById(13L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getAirlineById(13L));
    }

    @Test
    void createAirlineShouldSaveEntity() {
        Airline airline = new Airline();
        airline.setName("New Air");
        when(airlineRepository.save(airline)).thenReturn(airline);

        Airline saved = service.createAirline(airline);

        assertSame(airline, saved);
        verify(airlineRepository).save(airline);
    }

    @Test
    void deleteAirlineShouldDeleteWhenExists() {
        when(airlineRepository.existsById(10L)).thenReturn(true);

        service.deleteAirline(10L);

        verify(airlineRepository).deleteById(10L);
    }

    @Test
    void deleteAirlineShouldThrowWhenMissing() {
        when(airlineRepository.existsById(10L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.deleteAirline(10L));
        verify(airlineRepository, never()).deleteById(any(Long.class));
    }

    @Test
    void getAllAirportsShouldReturnRepositoryResult() {
        Airport airport = new Airport();
        airport.setAirportId(2L);
        when(airportRepository.findAll()).thenReturn(List.of(airport));

        List<Airport> response = service.getAllAirports();

        assertEquals(1, response.size());
        assertSame(airport, response.get(0));
    }

    @Test
    void createAirportShouldSaveEntity() {
        Airport airport = new Airport();
        airport.setName("Airport One");
        when(airportRepository.save(airport)).thenReturn(airport);

        Airport saved = service.createAirport(airport);

        assertSame(airport, saved);
    }

    @Test
    void updateAirportShouldThrowWhenAirportMissing() {
        when(airportRepository.findById(40L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateAirport(40L, new Airport()));
        verify(airportRepository, never()).save(any(Airport.class));
    }

    @Test
    void updateAirportShouldApplyMutableFieldsAndSave() {
        Airport existing = new Airport();
        existing.setAirportId(7L);
        existing.setName("Old Name");
        existing.setIataCode("OLD");
        existing.setCity("Old City");
        existing.setCountry("Old Country");
        existing.setTimezone("Old/Zone");
        existing.setLatitude(1.1);
        existing.setLongitude(2.2);

        Airport update = new Airport();
        update.setName("New Name");
        update.setIataCode("NEW");
        update.setCity("New City");
        update.setCountry("New Country");
        update.setTimezone("Asia/Kolkata");
        update.setLatitude(3.3);
        update.setLongitude(4.4);

        when(airportRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(airportRepository.save(existing)).thenReturn(existing);

        Airport saved = service.updateAirport(7L, update);

        assertEquals("New Name", saved.getName());
        assertEquals("NEW", saved.getIataCode());
        assertEquals("New City", saved.getCity());
        assertEquals("New Country", saved.getCountry());
        assertEquals("Asia/Kolkata", saved.getTimezone());
        assertEquals(3.3, saved.getLatitude());
        assertEquals(4.4, saved.getLongitude());
    }

    @Test
    void deleteAirportShouldDeleteWhenExists() {
        when(airportRepository.existsById(5L)).thenReturn(true);

        service.deleteAirport(5L);

        verify(airportRepository).deleteById(5L);
    }

    @Test
    void deleteAirportShouldThrowWhenMissing() {
        when(airportRepository.existsById(5L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.deleteAirport(5L));
        verify(airportRepository, never()).deleteById(any(Long.class));
    }

    @Test
    void assignStaffToAirlineShouldCreateAssignmentWhenMissing() {
        when(airlineRepository.existsById(11L)).thenReturn(true);
        when(staffAirlineAssignmentRepository.findByUserId(21L)).thenReturn(Optional.empty());
        when(staffAirlineAssignmentRepository.save(any(StaffAirlineAssignment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Long airlineId = service.assignStaffToAirline(21L, 11L);

        assertEquals(11L, airlineId);
        ArgumentCaptor<StaffAirlineAssignment> captor = ArgumentCaptor.forClass(StaffAirlineAssignment.class);
        verify(staffAirlineAssignmentRepository).save(captor.capture());
        assertEquals(21L, captor.getValue().getUserId());
        assertEquals(11L, captor.getValue().getAirlineId());
    }

    @Test
    void assignStaffToAirlineShouldUpdateExistingAssignment() {
        StaffAirlineAssignment existing = new StaffAirlineAssignment();
        existing.setAssignmentId(20L);
        existing.setUserId(55L);
        existing.setAirlineId(1L);

        when(airlineRepository.existsById(8L)).thenReturn(true);
        when(staffAirlineAssignmentRepository.findByUserId(55L)).thenReturn(Optional.of(existing));
        when(staffAirlineAssignmentRepository.save(existing)).thenReturn(existing);

        Long airlineId = service.assignStaffToAirline(55L, 8L);

        assertEquals(8L, airlineId);
        assertEquals(8L, existing.getAirlineId());
    }

    @Test
    void assignStaffToAirlineShouldThrowWhenAirlineMissing() {
        when(airlineRepository.existsById(8L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.assignStaffToAirline(55L, 8L));
        verify(staffAirlineAssignmentRepository, never()).findByUserId(any(Long.class));
    }

    @Test
    void getAssignedAirlineIdShouldThrowWhenNoAssignmentPresent() {
        when(staffAirlineAssignmentRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getAssignedAirlineId(99L));
    }

    @Test
    void getAssignedAirlineIdShouldReturnAssignedAirlineWhenPresent() {
        StaffAirlineAssignment assignment = new StaffAirlineAssignment();
        assignment.setUserId(99L);
        assignment.setAirlineId(17L);
        when(staffAirlineAssignmentRepository.findByUserId(99L)).thenReturn(Optional.of(assignment));

        Long airlineId = service.getAssignedAirlineId(99L);

        assertEquals(17L, airlineId);
    }
}
