package com.skybooker.airlineairportservice.service;

import com.skybooker.airlineairportservice.entity.Airline;
import com.skybooker.airlineairportservice.entity.Airport;
import com.skybooker.airlineairportservice.repository.AirlineRepository;
import com.skybooker.airlineairportservice.repository.AirportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceDataSeederTest {

    @Mock
    private AirlineRepository airlineRepository;

    @Mock
    private AirportRepository airportRepository;

    @InjectMocks
    private ReferenceDataSeeder referenceDataSeeder;

    @Test
    void runShouldSeedAirlinesAndAirportsWithExpectedDefaults() throws Exception {
        when(airlineRepository.findByIataCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(airportRepository.findByIataCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(airlineRepository.save(any(Airline.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(airportRepository.save(any(Airport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        referenceDataSeeder.run();

        ArgumentCaptor<Airline> airlineCaptor = ArgumentCaptor.forClass(Airline.class);
        verify(airlineRepository, atLeast(10)).save(airlineCaptor.capture());
        assertTrue(airlineCaptor.getAllValues().stream().allMatch(Airline::isActive));
        assertTrue(airlineCaptor.getAllValues().stream().allMatch(a -> "India".equals(a.getCountry())));

        ArgumentCaptor<Airport> airportCaptor = ArgumentCaptor.forClass(Airport.class);
        verify(airportRepository, atLeast(13)).save(airportCaptor.capture());
        Map<String, Airport> byCode = airportCaptor.getAllValues().stream()
            .collect(Collectors.toMap(Airport::getIataCode, Function.identity(), (first, second) -> second));

        assertEquals("UAE", byCode.get("DXB").getCountry());
        assertEquals("Asia/Dubai", byCode.get("DXB").getTimezone());
        assertEquals("United Kingdom", byCode.get("LHR").getCountry());
        assertEquals("Europe/London", byCode.get("LHR").getTimezone());
        assertEquals("USA", byCode.get("JFK").getCountry());
        assertEquals("America/New_York", byCode.get("JFK").getTimezone());
        assertEquals("India", byCode.get("DEL").getCountry());
        assertEquals("Asia/Kolkata", byCode.get("DEL").getTimezone());
    }

    @Test
    void runShouldReuseExistingRecordsDuringUpsert() throws Exception {
        Airline existingAirline = new Airline();
        existingAirline.setAirlineId(200L);
        existingAirline.setIataCode("AI");
        existingAirline.setName("Legacy Name");

        Airport existingAirport = new Airport();
        existingAirport.setAirportId(300L);
        existingAirport.setIataCode("DEL");
        existingAirport.setName("Legacy Airport");

        when(airlineRepository.findByIataCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(airportRepository.findByIataCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(airlineRepository.findByIataCodeIgnoreCase("AI")).thenReturn(Optional.of(existingAirline));
        when(airportRepository.findByIataCodeIgnoreCase("DEL")).thenReturn(Optional.of(existingAirport));
        when(airlineRepository.save(any(Airline.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(airportRepository.save(any(Airport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        referenceDataSeeder.run();

        assertEquals("Air India", existingAirline.getName());
        assertTrue(existingAirline.isActive());
        ArgumentCaptor<Airline> airlineCaptor = ArgumentCaptor.forClass(Airline.class);
        verify(airlineRepository, atLeast(10)).save(airlineCaptor.capture());
        assertTrue(airlineCaptor.getAllValues().stream().anyMatch(saved -> saved == existingAirline));

        assertEquals("Indira Gandhi International Airport", existingAirport.getName());
        assertEquals("Delhi", existingAirport.getCity());
        ArgumentCaptor<Airport> airportCaptor = ArgumentCaptor.forClass(Airport.class);
        verify(airportRepository, atLeast(13)).save(airportCaptor.capture());
        assertTrue(airportCaptor.getAllValues().stream().anyMatch(saved -> saved == existingAirport));
    }
}
