package com.skybooker.flightservice.service.impl;

import com.skybooker.flightservice.dto.ApiMessageResponse;
import com.skybooker.flightservice.dto.CreateFlightRequest;
import com.skybooker.flightservice.dto.FlightResponse;
import com.skybooker.flightservice.dto.RoundTripSearchResponse;
import com.skybooker.flightservice.dto.UpdateFlightRequest;
import com.skybooker.flightservice.dto.UpdateFlightStatusRequest;
import com.skybooker.flightservice.entity.DepartureWindow;
import com.skybooker.flightservice.entity.Flight;
import com.skybooker.flightservice.entity.FlightStatus;
import com.skybooker.flightservice.entity.SeatClass;
import com.skybooker.flightservice.exception.BadRequestException;
import com.skybooker.flightservice.exception.ResourceNotFoundException;
import com.skybooker.flightservice.repository.FlightRepository;
import com.skybooker.flightservice.service.AirlineCatalogClient;
import com.skybooker.flightservice.service.FlightService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class FlightServiceImpl implements FlightService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlightServiceImpl.class);

    private final FlightRepository flightRepository;
    private final AirlineCatalogClient airlineCatalogClient;

    public FlightServiceImpl(FlightRepository flightRepository,
                             AirlineCatalogClient airlineCatalogClient) {
        this.flightRepository = flightRepository;
        this.airlineCatalogClient = airlineCatalogClient;
    }

    @Override
    public List<FlightResponse> getManagedFlights(LocalDate flightDate, Long airlineId) {
        Specification<Flight> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (flightDate != null) {
                LocalDateTime dayStart = flightDate.atStartOfDay();
                LocalDateTime dayEnd = flightDate.atTime(LocalTime.MAX);
                predicates.add(criteriaBuilder.between(root.get("departureTime"), dayStart, dayEnd));
            }

            if (airlineId != null) {
                predicates.add(criteriaBuilder.equal(root.get("airlineId"), airlineId));
            }

            return predicates.isEmpty()
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return flightRepository.findAll(specification)
            .stream()
            .sorted(Comparator.comparing(Flight::getDepartureTime))
            .map(flight -> toFlightResponse(flight, SeatClass.ECONOMY))
            .toList();
    }

    @Override
    public FlightResponse addFlight(CreateFlightRequest request) {
        validateScheduleTimes(request.getDepartureTime(), request.getArrivalTime());
        validateSeats(request.getTotalSeats(), request.getAvailableSeats());
        validateRouteAndStops(
            request.getOriginAirportCode(),
            request.getDestinationAirportCode(),
            request.getNumberOfStops(),
            request.getViaAirportCode()
        );
        airlineCatalogClient.validateAirlineExists(request.getAirlineId());

        if (flightRepository.existsByFlightNumber(request.getFlightNumber())) {
            throw new BadRequestException("Flight number already exists");
        }

        Flight flight = new Flight();
        mapCreateRequestToEntity(request, flight);
        Flight savedFlight = flightRepository.save(flight);
        LOGGER.info("Flight created with number: {}", savedFlight.getFlightNumber());

        return toFlightResponse(savedFlight, SeatClass.ECONOMY);
    }

    @Override
    public FlightResponse updateFlight(Long flightId, UpdateFlightRequest request) {
        validateScheduleTimes(request.getDepartureTime(), request.getArrivalTime());
        validateSeats(request.getTotalSeats(), request.getAvailableSeats());
        validateRouteAndStops(
            request.getOriginAirportCode(),
            request.getDestinationAirportCode(),
            request.getNumberOfStops(),
            request.getViaAirportCode()
        );
        airlineCatalogClient.validateAirlineExists(request.getAirlineId());

        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found with id: " + flightId));

        flightRepository.findByFlightNumber(request.getFlightNumber())
                .filter(existing -> !existing.getFlightId().equals(flightId))
                .ifPresent(existing -> {
                    throw new BadRequestException("Flight number already exists");
                });

        mapUpdateRequestToEntity(request, flight);
        Flight savedFlight = flightRepository.save(flight);
        LOGGER.info("Flight updated with id: {}", savedFlight.getFlightId());

        return toFlightResponse(savedFlight, SeatClass.ECONOMY);
    }

    @Override
    public ApiMessageResponse deleteFlight(Long flightId) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found with id: " + flightId));

        flightRepository.delete(flight);
        LOGGER.info("Flight deleted with id: {}", flightId);
        return new ApiMessageResponse("Flight deleted successfully");
    }

    @Override
    public FlightResponse getFlightById(Long flightId) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found with id: " + flightId));

        return toFlightResponse(flight, SeatClass.ECONOMY);
    }

    @Override
    @Transactional
    public FlightResponse adjustAvailableSeats(Long flightId, Integer seatDelta) {
        Flight flight = flightRepository.findById(flightId)
            .orElseThrow(() -> new ResourceNotFoundException("Flight not found with id: " + flightId));

        int delta = seatDelta == null ? 0 : seatDelta;
        if (delta == 0) {
            return toFlightResponse(flight, SeatClass.ECONOMY);
        }

        int currentAvailable = flight.getAvailableSeats() == null ? 0 : flight.getAvailableSeats();
        int totalSeats = flight.getTotalSeats() == null ? 0 : flight.getTotalSeats();
        long candidateAvailable = (long) currentAvailable + delta;

        if (candidateAvailable < 0) {
            throw new BadRequestException("Not enough seats available");
        }
        if (candidateAvailable > totalSeats) {
            throw new BadRequestException("Available seats cannot exceed total seats");
        }

        flight.setAvailableSeats((int) candidateAvailable);
        Flight savedFlight = flightRepository.save(flight);
        LOGGER.info(
            "Adjusted available seats for flight {} by {} ({} -> {})",
            flightId,
            delta,
            currentAvailable,
            savedFlight.getAvailableSeats()
        );
        return toFlightResponse(savedFlight, SeatClass.ECONOMY);
    }

    @Override
    public FlightResponse updateFlightStatus(UpdateFlightStatusRequest request) {
        Flight flight = flightRepository.findById(request.getFlightId())
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found with id: " + request.getFlightId()));

        flight.setStatus(request.getStatus());
        Flight savedFlight = flightRepository.save(flight);
        LOGGER.info("Flight status updated for id: {} to {}", savedFlight.getFlightId(), request.getStatus());

        return toFlightResponse(savedFlight, SeatClass.ECONOMY);
    }

    @Override
    public List<FlightResponse> searchOneWayFlights(String originAirportCode,
                                                    String destinationAirportCode,
                                                    LocalDate journeyDate,
                                                    BigDecimal minPrice,
                                                    BigDecimal maxPrice,
                                                    Long airlineId,
                                                    DepartureWindow departureWindow,
                                                    Integer maxStops,
                                                    SeatClass seatClass,
                                                    String sortBy) {

        if (originAirportCode == null || destinationAirportCode == null || journeyDate == null) {
            throw new BadRequestException("Origin, destination, and journeyDate are required");
        }

        if (maxStops != null && maxStops < 0) {
            throw new BadRequestException("maxStops cannot be negative");
        }

        SeatClass safeSeatClass = seatClass == null ? SeatClass.ECONOMY : seatClass;

        List<Flight> flights = flightRepository.findAll(buildSearchSpec(
                originAirportCode,
                destinationAirportCode,
                journeyDate,
                airlineId,
                departureWindow
        ));

        List<FlightResponse> responses = flights.stream()
                .filter(flight -> flight.getStatus() != FlightStatus.CANCELLED)
                .map(flight -> toFlightResponse(flight, safeSeatClass))
                .filter(response -> maxStops == null || response.getNumberOfStops() <= maxStops)
                .filter(response -> minPrice == null || response.getDisplayedPrice().compareTo(minPrice) >= 0)
                .filter(response -> maxPrice == null || response.getDisplayedPrice().compareTo(maxPrice) <= 0)
                .toList();

        return sortFlights(responses, sortBy);
    }

    @Override
    public RoundTripSearchResponse searchRoundTripFlights(String originAirportCode,
                                                          String destinationAirportCode,
                                                          LocalDate onwardDate,
                                                          LocalDate returnDate,
                                                          BigDecimal minPrice,
                                                          BigDecimal maxPrice,
                                                          Long airlineId,
                                                          DepartureWindow departureWindow,
                                                          Integer maxStops,
                                                          SeatClass seatClass,
                                                          String sortBy) {

        if (returnDate == null) {
            throw new BadRequestException("returnDate is required for round-trip search");
        }

        List<FlightResponse> outboundFlights = searchOneWayFlights(
                originAirportCode,
                destinationAirportCode,
                onwardDate,
                minPrice,
                maxPrice,
                airlineId,
                departureWindow,
                maxStops,
                seatClass,
                sortBy
        );

        List<FlightResponse> returnFlights = searchOneWayFlights(
                destinationAirportCode,
                originAirportCode,
                returnDate,
                minPrice,
                maxPrice,
                airlineId,
                departureWindow,
                maxStops,
                seatClass,
                sortBy
        );

        return new RoundTripSearchResponse(outboundFlights, returnFlights);
    }

    private Specification<Flight> buildSearchSpec(String originAirportCode,
                                                  String destinationAirportCode,
                                                  LocalDate journeyDate,
                                                  Long airlineId,
                                                  DepartureWindow departureWindow) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(criteriaBuilder.upper(root.get("originAirportCode")), originAirportCode.toUpperCase()));
            predicates.add(criteriaBuilder.equal(criteriaBuilder.upper(root.get("destinationAirportCode")), destinationAirportCode.toUpperCase()));
            predicates.add(criteriaBuilder.greaterThan(root.get("availableSeats"), 0));

            LocalDateTime dayStart = journeyDate.atStartOfDay();
            LocalDateTime dayEnd = journeyDate.atTime(LocalTime.MAX);
            predicates.add(criteriaBuilder.between(root.get("departureTime"), dayStart, dayEnd));

            if (airlineId != null) {
                predicates.add(criteriaBuilder.equal(root.get("airlineId"), airlineId));
            }

            if (departureWindow != null) {
                LocalDateTime fromTime = dayStart;
                LocalDateTime toTime = dayEnd;

                switch (departureWindow) {
                    case MORNING -> {
                        fromTime = journeyDate.atTime(5, 0);
                        toTime = journeyDate.atTime(11, 59, 59);
                    }
                    case AFTERNOON -> {
                        fromTime = journeyDate.atTime(12, 0);
                        toTime = journeyDate.atTime(16, 59, 59);
                    }
                    case EVENING -> {
                        fromTime = journeyDate.atTime(17, 0);
                        toTime = journeyDate.atTime(20, 59, 59);
                    }
                    case NIGHT -> {
                        fromTime = journeyDate.atTime(21, 0);
                        toTime = journeyDate.atTime(23, 59, 59);
                    }
                }
                predicates.add(criteriaBuilder.between(root.get("departureTime"), fromTime, toTime));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<FlightResponse> sortFlights(List<FlightResponse> flights, String sortBy) {
        List<FlightResponse> sortedFlights = new ArrayList<>(flights);

        if (sortBy == null || sortBy.isBlank() || "price_asc".equalsIgnoreCase(sortBy)) {
            sortedFlights.sort(Comparator.comparing(FlightResponse::getDisplayedPrice));
            return sortedFlights;
        }

        switch (sortBy.toLowerCase()) {
            case "price_desc" -> sortedFlights.sort(Comparator.comparing(FlightResponse::getDisplayedPrice).reversed());
            case "departure_asc" -> sortedFlights.sort(Comparator.comparing(FlightResponse::getDepartureTime));
            case "departure_desc" -> sortedFlights.sort(Comparator.comparing(FlightResponse::getDepartureTime).reversed());
            case "duration_asc" -> sortedFlights.sort(Comparator.comparing(FlightResponse::getDurationMinutes));
            case "duration_desc" -> sortedFlights.sort(Comparator.comparing(FlightResponse::getDurationMinutes).reversed());
            default -> sortedFlights.sort(Comparator.comparing(FlightResponse::getDisplayedPrice));
        }

        return sortedFlights;
    }

    private FlightResponse toFlightResponse(Flight flight, SeatClass seatClass) {
        BigDecimal displayedPrice = calculateClassPrice(flight.getBasePrice(), seatClass);

        FlightResponse response = new FlightResponse();
        response.setFlightId(flight.getFlightId());
        response.setFlightNumber(flight.getFlightNumber());
        response.setAirlineId(flight.getAirlineId());
        response.setOriginAirportCode(flight.getOriginAirportCode());
        response.setDestinationAirportCode(flight.getDestinationAirportCode());
        response.setDepartureTime(flight.getDepartureTime());
        response.setArrivalTime(flight.getArrivalTime());
        response.setDurationMinutes(flight.getDurationMinutes());
        response.setStatus(flight.getStatus());
        response.setAircraftType(flight.getAircraftType());
        response.setTotalSeats(flight.getTotalSeats());
        response.setAvailableSeats(flight.getAvailableSeats());
        response.setBasePrice(flight.getBasePrice());
        response.setSeatClass(seatClass);
        response.setDisplayedPrice(displayedPrice);
        response.setNumberOfStops(flight.getNumberOfStops() == null ? 0 : flight.getNumberOfStops());
        response.setViaAirportCode(flight.getViaAirportCode());
        return response;
    }

    private BigDecimal calculateClassPrice(BigDecimal basePrice, SeatClass seatClass) {
        BigDecimal multiplier = switch (seatClass) {
            case PREMIUM_ECONOMY -> new BigDecimal("1.20");
            case BUSINESS -> new BigDecimal("1.70");
            case FIRST -> new BigDecimal("2.30");
            default -> BigDecimal.ONE;
        };

        return basePrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private void validateScheduleTimes(LocalDateTime departureTime, LocalDateTime arrivalTime) {
        if (arrivalTime.isBefore(departureTime) || arrivalTime.isEqual(departureTime)) {
            throw new BadRequestException("Arrival time must be after departure time");
        }
    }

    private void validateSeats(Integer totalSeats, Integer availableSeats) {
        if (availableSeats > totalSeats) {
            throw new BadRequestException("Available seats cannot be greater than total seats");
        }
    }

    private void validateRouteAndStops(String originAirportCode,
                                       String destinationAirportCode,
                                       Integer numberOfStops,
                                       String viaAirportCode) {
        String safeOrigin = originAirportCode == null ? "" : originAirportCode.trim().toUpperCase();
        String safeDestination = destinationAirportCode == null ? "" : destinationAirportCode.trim().toUpperCase();
        if (safeOrigin.isBlank() || safeDestination.isBlank()) {
            throw new BadRequestException("Origin and destination airport codes are required");
        }
        if (safeOrigin.equals(safeDestination)) {
            throw new BadRequestException("Origin and destination cannot be the same");
        }

        int stops = numberOfStops == null ? 0 : numberOfStops;
        if (stops < 0) {
            throw new BadRequestException("numberOfStops cannot be negative");
        }

        if (stops == 0 && viaAirportCode != null && !viaAirportCode.isBlank()) {
            throw new BadRequestException("viaAirportCode must be empty for direct flights");
        }

        if (stops > 0) {
            String safeVia = normalizeViaAirportCode(viaAirportCode);
            if (safeVia == null) {
                throw new BadRequestException("viaAirportCode is required for stop flights");
            }
            if (safeVia.equals(safeOrigin) || safeVia.equals(safeDestination)) {
                throw new BadRequestException("viaAirportCode must be different from origin and destination");
            }
        }
    }

    private void mapCreateRequestToEntity(CreateFlightRequest request, Flight flight) {
        flight.setFlightNumber(request.getFlightNumber());
        flight.setAirlineId(request.getAirlineId());
        flight.setOriginAirportCode(request.getOriginAirportCode());
        flight.setDestinationAirportCode(request.getDestinationAirportCode());
        flight.setDepartureTime(request.getDepartureTime());
        flight.setArrivalTime(request.getArrivalTime());
        flight.setDurationMinutes(request.getDurationMinutes());
        flight.setNumberOfStops(request.getNumberOfStops() == null ? 0 : request.getNumberOfStops());
        flight.setViaAirportCode(normalizeViaAirportCode(request.getViaAirportCode()));
        flight.setStatus(request.getStatus());
        flight.setAircraftType(request.getAircraftType());
        flight.setTotalSeats(request.getTotalSeats());
        flight.setAvailableSeats(request.getAvailableSeats());
        flight.setBasePrice(request.getBasePrice());
    }

    private void mapUpdateRequestToEntity(UpdateFlightRequest request, Flight flight) {
        flight.setFlightNumber(request.getFlightNumber());
        flight.setAirlineId(request.getAirlineId());
        flight.setOriginAirportCode(request.getOriginAirportCode());
        flight.setDestinationAirportCode(request.getDestinationAirportCode());
        flight.setDepartureTime(request.getDepartureTime());
        flight.setArrivalTime(request.getArrivalTime());
        flight.setDurationMinutes(request.getDurationMinutes());
        flight.setNumberOfStops(request.getNumberOfStops() == null ? 0 : request.getNumberOfStops());
        flight.setViaAirportCode(normalizeViaAirportCode(request.getViaAirportCode()));
        flight.setStatus(request.getStatus());
        flight.setAircraftType(request.getAircraftType());
        flight.setTotalSeats(request.getTotalSeats());
        flight.setAvailableSeats(request.getAvailableSeats());
        flight.setBasePrice(request.getBasePrice());
    }

    private String normalizeViaAirportCode(String viaAirportCode) {
        if (viaAirportCode == null || viaAirportCode.isBlank()) {
            return null;
        }
        return viaAirportCode.trim().toUpperCase();
    }
}
