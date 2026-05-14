package com.skybooker.flightservice.service;

import com.skybooker.flightservice.entity.Flight;
import com.skybooker.flightservice.entity.FlightStatus;
import com.skybooker.flightservice.repository.FlightRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Component
public class FlightDataSeeder implements CommandLineRunner {
    private static final int SEED_WINDOW_DAYS = 40;
    private static final double DAY_WITH_FLIGHTS_PROBABILITY = 0.70d;
    private static final int MIN_FLIGHTS_PER_ROUTE_DAY = 1;
    private static final int MAX_FLIGHTS_PER_ROUTE_DAY = 3;

    private static final List<String> AIRPORT_CODES = List.of(
        "DEL", "MUM", "BLR", "HYD", "CCU", "GOI", "PNQ", "AMD", "MAA",
        "DXB", "SIN", "LHR", "JFK"
    );

    private static final Set<String> DOMESTIC_CODES = Set.of(
        "DEL", "MUM", "BLR", "HYD", "CCU", "GOI", "PNQ", "AMD", "MAA"
    );

    private static final List<String> AIRCRAFT_TYPES = List.of(
        "Airbus A320", "Airbus A321", "Boeing 737", "Boeing 787", "Airbus A220"
    );

    private static final List<RouteSeed> ROUTES = List.of(
        new RouteSeed("DEL", "MUM"),
        new RouteSeed("DEL", "BLR"),
        new RouteSeed("DEL", "HYD"),
        new RouteSeed("DEL", "DXB"),
        new RouteSeed("MUM", "BLR"),
        new RouteSeed("MUM", "HYD"),
        new RouteSeed("MUM", "GOI"),
        new RouteSeed("BLR", "HYD"),
        new RouteSeed("BLR", "SIN"),
        new RouteSeed("HYD", "DXB"),
        new RouteSeed("CCU", "DEL"),
        new RouteSeed("CCU", "BLR"),
        new RouteSeed("PNQ", "DEL"),
        new RouteSeed("AMD", "MUM"),
        new RouteSeed("MAA", "DEL"),
        new RouteSeed("SIN", "LHR")
    );

    private static final Map<String, double[]> AIRPORT_COORDINATES = Map.ofEntries(
        Map.entry("DEL", new double[] {28.5562, 77.1000}),
        Map.entry("MUM", new double[] {19.0896, 72.8656}),
        Map.entry("BLR", new double[] {13.1989, 77.7063}),
        Map.entry("HYD", new double[] {17.2403, 78.4294}),
        Map.entry("CCU", new double[] {22.6547, 88.4467}),
        Map.entry("GOI", new double[] {15.3808, 73.8314}),
        Map.entry("PNQ", new double[] {18.5822, 73.9197}),
        Map.entry("AMD", new double[] {23.0772, 72.6347}),
        Map.entry("MAA", new double[] {12.9900, 80.1693}),
        Map.entry("DXB", new double[] {25.2532, 55.3657}),
        Map.entry("SIN", new double[] {1.3644, 103.9915}),
        Map.entry("LHR", new double[] {51.4700, -0.4543}),
        Map.entry("JFK", new double[] {40.6413, -73.7781})
    );

    private final FlightRepository flightRepository;
    private final AirlineCatalogClient airlineCatalogClient;
    private final Random random = new Random(42);

    @Value("${app.seed-data:false}")
    private boolean seedData;

    public FlightDataSeeder(FlightRepository flightRepository,
                            AirlineCatalogClient airlineCatalogClient) {
        this.flightRepository = flightRepository;
        this.airlineCatalogClient = airlineCatalogClient;
    }

    void setSeedData(boolean seedData) {
        this.seedData = seedData;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedData) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate windowEndDate = today.plusDays(SEED_WINDOW_DAYS);
        LocalDateTime windowStart = today.atStartOfDay();
        LocalDateTime windowEndInclusive = windowEndDate.atTime(LocalTime.MAX);

        cleanupFlightsOutsideWindow(windowStart, windowEndInclusive);

        List<ActiveAirline> activeAirlines = resolveActiveAirlines();
        if (activeAirlines.isEmpty()) {
            return;
        }

        Set<String> usedFlightNumbers = new HashSet<>(
            flightRepository.findAll().stream().map(Flight::getFlightNumber).filter(number -> number != null && !number.isBlank()).toList()
        );
        Map<String, Integer> sequenceByAirlineCode = new HashMap<>();
        bootstrapSequences(usedFlightNumbers, sequenceByAirlineCode);

        Set<Long> activeAirlineIds = activeAirlines.stream().map(ActiveAirline::id).collect(HashSet::new, Set::add, Set::addAll);
        Set<String> supportedRoutes = ROUTES.stream()
            .map(route -> routeKey(route.origin(), route.destination()))
            .collect(HashSet::new, Set::add, Set::addAll);

        List<Flight> existingInWindow = flightRepository.findByDepartureTimeBetween(windowStart, windowEndInclusive);
        RouteNormalizationState normalizationState = normalizeExistingRouteDensity(existingInWindow, activeAirlineIds, supportedRoutes);

        Map<RouteDateKey, Integer> routeDayCount = new HashMap<>(normalizationState.routeDayCount());
        Map<LocalDate, Map<Long, Integer>> airlineUsageByDay = new HashMap<>(normalizationState.airlineUsageByDay());
        Set<String> existingSignatures = new HashSet<>(normalizationState.signatures());

        List<Flight> generated = new ArrayList<>();

        for (RouteSeed route : ROUTES) {
            for (int dayOffset = 0; dayOffset <= SEED_WINDOW_DAYS; dayOffset += 1) {
                LocalDate travelDate = today.plusDays(dayOffset);
                RouteDateKey routeDateKey = new RouteDateKey(route.origin(), route.destination(), travelDate);

                int existingCount = routeDayCount.getOrDefault(routeDateKey, 0);
                if (existingCount >= MAX_FLIGHTS_PER_ROUTE_DAY) {
                    continue;
                }

                boolean shouldSeedThisDay = existingCount > 0 || random.nextDouble() < DAY_WITH_FLIGHTS_PROBABILITY;
                if (!shouldSeedThisDay) {
                    continue;
                }

                int targetForDay = MIN_FLIGHTS_PER_ROUTE_DAY + random.nextInt(MAX_FLIGHTS_PER_ROUTE_DAY - MIN_FLIGHTS_PER_ROUTE_DAY + 1);
                int toGenerate = Math.max(0, targetForDay - existingCount);

                for (int index = 0; index < toGenerate; index += 1) {
                    ActiveAirline airline = chooseAirlineForDay(activeAirlines, travelDate, airlineUsageByDay);
                    Flight flight = createUniqueFlightForRouteDate(
                        route,
                        travelDate,
                        airline,
                        usedFlightNumbers,
                        sequenceByAirlineCode,
                        existingSignatures
                    );

                    if (flight == null) {
                        continue;
                    }

                    generated.add(flight);
                    routeDayCount.merge(routeDateKey, 1, Integer::sum);
                    airlineUsageByDay
                        .computeIfAbsent(travelDate, key -> new HashMap<>())
                        .merge(airline.id(), 1, Integer::sum);
                }
            }
        }

        if (!generated.isEmpty()) {
            flightRepository.saveAll(generated);
        }
    }

    private void cleanupFlightsOutsideWindow(LocalDateTime windowStart, LocalDateTime windowEndInclusive) {
        flightRepository.deleteByDepartureTimeBefore(windowStart);
        flightRepository.deleteByDepartureTimeAfter(windowEndInclusive);
    }

    private RouteNormalizationState normalizeExistingRouteDensity(List<Flight> existingFlights,
                                                                  Set<Long> activeAirlineIds,
                                                                  Set<String> supportedRoutes) {
        Map<RouteDateKey, List<Flight>> grouped = new HashMap<>();
        List<Flight> obsoleteFlights = new ArrayList<>();
        for (Flight flight : existingFlights) {
            if (flight.getDepartureTime() == null
                || flight.getAirlineId() == null
                || !isValidRoute(flight.getOriginAirportCode(), flight.getDestinationAirportCode())) {
                obsoleteFlights.add(flight);
                continue;
            }

            if (!activeAirlineIds.contains(flight.getAirlineId())
                || !supportedRoutes.contains(routeKey(flight.getOriginAirportCode(), flight.getDestinationAirportCode()))) {
                obsoleteFlights.add(flight);
                continue;
            }

            RouteDateKey key = new RouteDateKey(
                flight.getOriginAirportCode(),
                flight.getDestinationAirportCode(),
                flight.getDepartureTime().toLocalDate()
            );
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(flight);
        }

        List<Flight> overflowFlights = new ArrayList<>();
        Map<RouteDateKey, Integer> routeDayCount = new HashMap<>();
        Map<LocalDate, Map<Long, Integer>> airlineUsageByDay = new HashMap<>();
        Set<String> signatures = new HashSet<>();

        for (Map.Entry<RouteDateKey, List<Flight>> entry : grouped.entrySet()) {
            List<Flight> flights = entry.getValue();
            flights.sort(Comparator.comparing(Flight::getDepartureTime));

            if (flights.size() > MAX_FLIGHTS_PER_ROUTE_DAY) {
                overflowFlights.addAll(flights.subList(MAX_FLIGHTS_PER_ROUTE_DAY, flights.size()));
                flights = new ArrayList<>(flights.subList(0, MAX_FLIGHTS_PER_ROUTE_DAY));
            }

            routeDayCount.put(entry.getKey(), flights.size());
            for (Flight flight : flights) {
                LocalDate date = flight.getDepartureTime().toLocalDate();
                airlineUsageByDay
                    .computeIfAbsent(date, ignored -> new HashMap<>())
                    .merge(flight.getAirlineId(), 1, Integer::sum);
                signatures.add(signature(flight.getAirlineId(), flight.getOriginAirportCode(), flight.getDestinationAirportCode(), flight.getDepartureTime()));
            }
        }

        List<Flight> toDelete = new ArrayList<>(obsoleteFlights);
        toDelete.addAll(overflowFlights);
        if (!toDelete.isEmpty()) {
            flightRepository.deleteAllInBatch(toDelete);
        }

        return new RouteNormalizationState(routeDayCount, airlineUsageByDay, signatures);
    }

    private List<ActiveAirline> resolveActiveAirlines() {
        List<Map<String, Object>> airlines = airlineCatalogClient.fetchAirlines();
        List<ActiveAirline> active = new ArrayList<>();

        for (Map<String, Object> airline : airlines) {
            Long id = toLong(airline.get("airlineId"));
            String iataCode = String.valueOf(airline.getOrDefault("iataCode", "")).trim().toUpperCase();
            boolean isActive = toBoolean(airline.get("active")) || toBoolean(airline.get("isActive"));

            if (id == null || iataCode.isBlank() || !isActive) {
                continue;
            }

            active.add(new ActiveAirline(id, iataCode));
        }

        return active;
    }

    private ActiveAirline chooseAirlineForDay(List<ActiveAirline> airlines,
                                              LocalDate date,
                                              Map<LocalDate, Map<Long, Integer>> airlineUsageByDay) {
        Map<Long, Integer> dayUsage = airlineUsageByDay.computeIfAbsent(date, ignored -> new HashMap<>());
        int minUsage = airlines.stream()
            .mapToInt(airline -> dayUsage.getOrDefault(airline.id(), 0))
            .min()
            .orElse(0);

        List<ActiveAirline> candidates = airlines.stream()
            .filter(airline -> dayUsage.getOrDefault(airline.id(), 0) == minUsage)
            .toList();

        return candidates.get(random.nextInt(candidates.size()));
    }

    private Flight createUniqueFlightForRouteDate(RouteSeed route,
                                                   LocalDate travelDate,
                                                   ActiveAirline airline,
                                                   Set<String> usedFlightNumbers,
                                                   Map<String, Integer> sequenceByAirlineCode,
                                                   Set<String> existingSignatures) {
        for (int attempt = 0; attempt < 8; attempt += 1) {
            LocalDateTime departure = randomDepartureTime(travelDate);
            String signature = signature(airline.id(), route.origin(), route.destination(), departure);

            if (existingSignatures.contains(signature)
                || flightRepository.existsByAirlineIdAndOriginAirportCodeAndDestinationAirportCodeAndDepartureTime(
                    airline.id(),
                    route.origin(),
                    route.destination(),
                    departure
                )) {
                continue;
            }

            existingSignatures.add(signature);
            return buildFlight(route.origin(), route.destination(), departure, airline, usedFlightNumbers, sequenceByAirlineCode);
        }

        return null;
    }

    private LocalDateTime randomDepartureTime(LocalDate travelDate) {
        int slot = random.nextInt(3);

        LocalTime start;
        int totalMinutes;

        if (slot == 0) {
            start = LocalTime.of(6, 0);
            totalMinutes = 4 * 60;
        } else if (slot == 1) {
            start = LocalTime.of(12, 0);
            totalMinutes = 4 * 60;
        } else {
            start = LocalTime.of(18, 0);
            totalMinutes = 4 * 60;
        }

        return LocalDateTime.of(travelDate, start.plusMinutes(random.nextInt(totalMinutes + 1)));
    }

    private Flight buildFlight(String origin,
                               String destination,
                               LocalDateTime departure,
                               ActiveAirline airline,
                               Set<String> usedFlightNumbers,
                               Map<String, Integer> sequenceByAirlineCode) {
        String flightNumber = nextFlightNumber(airline.iataCode(), usedFlightNumbers, sequenceByAirlineCode);

        int durationMinutes = calculateDirectDuration(origin, destination);
        int totalSeats = random.nextInt(71) + 150;
        int availableSeats = Math.max((int) Math.round(totalSeats * (0.60 + (random.nextDouble() * 0.35))), 1);
        BigDecimal basePrice = calculateDynamicPrice(origin, destination, durationMinutes, departure.toLocalTime());

        Flight flight = new Flight();
        flight.setFlightNumber(flightNumber);
        flight.setAirlineId(airline.id());
        flight.setOriginAirportCode(origin);
        flight.setDestinationAirportCode(destination);
        flight.setDepartureTime(departure);
        flight.setArrivalTime(departure.plusMinutes(durationMinutes));
        flight.setDurationMinutes(durationMinutes);
        flight.setNumberOfStops(0);
        flight.setViaAirportCode(null);
        flight.setStatus(FlightStatus.ON_TIME);
        flight.setAircraftType(AIRCRAFT_TYPES.get(random.nextInt(AIRCRAFT_TYPES.size())));
        flight.setTotalSeats(totalSeats);
        flight.setAvailableSeats(availableSeats);
        flight.setBasePrice(basePrice);
        return flight;
    }

    private int calculateDirectDuration(String origin, String destination) {
        double distanceKm = haversineDistanceKm(origin, destination);
        double airborneMinutes = (distanceKm / 740d) * 60d;
        return (int) Math.round(airborneMinutes + 32d + random.nextInt(20));
    }

    private BigDecimal calculateDynamicPrice(String origin,
                                             String destination,
                                             int durationMinutes,
                                             LocalTime departureTime) {
        double distanceKm = haversineDistanceKm(origin, destination);
        boolean domesticRoute = isDomesticRoute(origin, destination);
        double base = domesticRoute ? 1400d + (distanceKm * 4.0d) : 5200d + (distanceKm * 5.4d);

        double timeFactor;
        if (departureTime.isBefore(LocalTime.NOON)) {
            timeFactor = 0.95d;
        } else if (departureTime.isBefore(LocalTime.of(17, 0))) {
            timeFactor = 1.04d;
        } else {
            timeFactor = 1.16d;
        }

        double durationFactor = 1.0d + Math.max(0, durationMinutes - 85) / 390d;
        double demandNoise = 0.93d + (random.nextDouble() * 0.24d);
        double internationalFactor = domesticRoute ? 1.0d : 1.24d;

        double computedPrice = base * timeFactor * durationFactor * internationalFactor * demandNoise;
        double boundedPrice = Math.max(computedPrice, domesticRoute ? 1900d : 9500d);
        return BigDecimal.valueOf(boundedPrice).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isDomesticRoute(String origin, String destination) {
        return DOMESTIC_CODES.contains(origin) && DOMESTIC_CODES.contains(destination);
    }

    private String nextFlightNumber(String airlineCode,
                                    Set<String> usedFlightNumbers,
                                    Map<String, Integer> sequenceByAirlineCode) {
        int sequence = sequenceByAirlineCode.getOrDefault(airlineCode, 500);
        while (true) {
            sequence += 1;
            String candidate = airlineCode + "-" + sequence;
            if (usedFlightNumbers.add(candidate) && !flightRepository.existsByFlightNumber(candidate)) {
                sequenceByAirlineCode.put(airlineCode, sequence);
                return candidate;
            }
        }
    }

    private void bootstrapSequences(Set<String> usedFlightNumbers,
                                    Map<String, Integer> sequenceByAirlineCode) {
        for (String flightNumber : usedFlightNumbers) {
            if (flightNumber == null || !flightNumber.contains("-")) {
                continue;
            }
            String[] parts = flightNumber.split("-", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                int currentSequence = Integer.parseInt(parts[1]);
                sequenceByAirlineCode.merge(parts[0], currentSequence, Math::max);
            } catch (NumberFormatException ignored) {
                // skip invalid historical values
            }
        }
    }

    private boolean isValidRoute(String origin, String destination) {
        return origin != null
            && destination != null
            && !origin.equals(destination)
            && AIRPORT_CODES.contains(origin)
            && AIRPORT_CODES.contains(destination);
    }

    private String signature(Long airlineId, String origin, String destination, LocalDateTime departureTime) {
        return airlineId + "|" + origin + "|" + destination + "|" + departureTime;
    }

    private String routeKey(String origin, String destination) {
        return origin + "->" + destination;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }

    private double haversineDistanceKm(String originCode, String destinationCode) {
        double[] origin = AIRPORT_COORDINATES.get(originCode);
        double[] destination = AIRPORT_COORDINATES.get(destinationCode);
        if (origin == null || destination == null) {
            return 1000d;
        }

        double latDistance = Math.toRadians(destination[0] - origin[0]);
        double lonDistance = Math.toRadians(destination[1] - origin[1]);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
            + Math.cos(Math.toRadians(origin[0])) * Math.cos(Math.toRadians(destination[0]))
            * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371d * c;
    }

    private record RouteSeed(String origin, String destination) {
    }

    private record ActiveAirline(Long id, String iataCode) {
    }

    private record RouteDateKey(String origin, String destination, LocalDate date) {
    }

    private record RouteNormalizationState(
        Map<RouteDateKey, Integer> routeDayCount,
        Map<LocalDate, Map<Long, Integer>> airlineUsageByDay,
        Set<String> signatures
    ) {
    }
}
