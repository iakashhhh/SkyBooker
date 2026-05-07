package com.skybooker.airlineairportservice.service;

import com.skybooker.airlineairportservice.entity.Airline;
import com.skybooker.airlineairportservice.entity.Airport;
import com.skybooker.airlineairportservice.repository.AirlineRepository;
import com.skybooker.airlineairportservice.repository.AirportRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ReferenceDataSeeder implements CommandLineRunner {

    private final AirlineRepository airlineRepository;
    private final AirportRepository airportRepository;

    public ReferenceDataSeeder(AirlineRepository airlineRepository,
                               AirportRepository airportRepository) {
        this.airlineRepository = airlineRepository;
        this.airportRepository = airportRepository;
    }

    @Override
    public void run(String... args) {
        seedAirlines();
        seedAirports();
    }

    private void seedAirlines() {
        upsertAirline("6E", "Indigo");
        upsertAirline("AI", "Air India");
        upsertAirline("UK", "Vistara");
        upsertAirline("QP", "Akasa Air");
        upsertAirline("SG", "SpiceJet");
        upsertAirline("I5", "AirAsia India");
        upsertAirline("IX", "Air India Express");
        upsertAirline("G8", "Go First");
        upsertAirline("EK", "Emirates");
        upsertAirline("SQ", "Singapore Airlines");
    }

    private void seedAirports() {
        upsertAirport("DEL", "Indira Gandhi International Airport", "Delhi", 28.5562, 77.1000);
        upsertAirport("MUM", "Chhatrapati Shivaji Maharaj International Airport", "Mumbai", 19.0896, 72.8656);
        upsertAirport("BLR", "Kempegowda International Airport", "Bengaluru", 13.1989, 77.7063);
        upsertAirport("HYD", "Rajiv Gandhi International Airport", "Hyderabad", 17.2403, 78.4294);
        upsertAirport("CCU", "Netaji Subhas Chandra Bose International Airport", "Kolkata", 22.6547, 88.4467);
        upsertAirport("GOI", "Goa International Airport", "Goa", 15.3808, 73.8314);
        upsertAirport("PNQ", "Pune International Airport", "Pune", 18.5822, 73.9197);
        upsertAirport("AMD", "Sardar Vallabhbhai Patel International Airport", "Ahmedabad", 23.0772, 72.6347);
        upsertAirport("CHE", "Chennai International Airport", "Chennai", 12.9900, 80.1693);
        upsertAirport("DXB", "Dubai International Airport", "Dubai", 25.2532, 55.3657);
        upsertAirport("SIN", "Singapore Changi Airport", "Singapore", 1.3644, 103.9915);
        upsertAirport("LHR", "London Heathrow Airport", "London", 51.4700, -0.4543);
        upsertAirport("JFK", "John F. Kennedy International Airport", "New York", 40.6413, -73.7781);
    }

    private void upsertAirline(String iataCode, String name) {
        Airline airline = airlineRepository.findByIataCodeIgnoreCase(iataCode)
            .orElseGet(Airline::new);
        airline.setIataCode(iataCode);
        airline.setName(name);
        airline.setCountry("India");
        airline.setActive(true);
        airlineRepository.save(airline);
    }

    private void upsertAirport(String iataCode,
                               String name,
                               String city,
                               double latitude,
                               double longitude) {
        Airport airport = airportRepository.findByIataCodeIgnoreCase(iataCode)
            .orElseGet(Airport::new);
        airport.setIataCode(iataCode);
        airport.setName(name);
        airport.setCity(city);
        airport.setCountry(resolveCountry(iataCode));
        airport.setTimezone(resolveTimezone(iataCode));
        airport.setLatitude(latitude);
        airport.setLongitude(longitude);
        airportRepository.save(airport);
    }

    private String resolveCountry(String iataCode) {
        return switch (iataCode) {
            case "DXB" -> "UAE";
            case "SIN" -> "Singapore";
            case "LHR" -> "United Kingdom";
            case "JFK" -> "USA";
            default -> "India";
        };
    }

    private String resolveTimezone(String iataCode) {
        return switch (iataCode) {
            case "DXB" -> "Asia/Dubai";
            case "SIN" -> "Asia/Singapore";
            case "LHR" -> "Europe/London";
            case "JFK" -> "America/New_York";
            default -> "Asia/Kolkata";
        };
    }
}
