package com.skybooker.flightservice.service;

import com.skybooker.flightservice.entity.Flight;
import com.skybooker.flightservice.repository.FlightRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FlightSeatSyncService {

    private final FlightRepository flightRepository;
    private final RestTemplate restTemplate;
    private final String bookingBaseUrl;
    private final Duration minSyncInterval;
    private Instant lastSyncAt = Instant.EPOCH;

    public FlightSeatSyncService(FlightRepository flightRepository,
                                 RestTemplate restTemplate,
                                 @Value("${flight.upstream.booking-base-url:http://localhost:8080}") String bookingBaseUrl,
                                 @Value("${flight.seat-sync-min-interval-ms:10000}") long minSyncIntervalMs) {
        this.flightRepository = flightRepository;
        this.restTemplate = restTemplate;
        this.bookingBaseUrl = bookingBaseUrl;
        this.minSyncInterval = Duration.ofMillis(Math.max(minSyncIntervalMs, 1000L));
    }

    public synchronized void syncIfNeeded() {
        if (Duration.between(lastSyncAt, Instant.now()).compareTo(minSyncInterval) < 0) {
            return;
        }
        syncSeats();
    }

    @Scheduled(fixedDelayString = "${flight.seat-sync-cron-interval-ms:60000}")
    public synchronized void syncSeats() {
        List<Map<String, Object>> bookings = fetchBookings();
        Map<Long, Integer> consumedSeatsByFlight = new HashMap<>();

        for (Map<String, Object> booking : bookings) {
            if (!isSeatConsumingStatus(booking.get("status"))) {
                continue;
            }

            Long flightId = toLong(booking.get("flightId"));
            if (flightId == null) {
                continue;
            }

            int seats = extractSeatCount(booking.get("seatIds"));
            consumedSeatsByFlight.merge(flightId, seats, Integer::sum);
        }

        List<Flight> flights = flightRepository.findAll();
        boolean dirty = false;
        for (Flight flight : flights) {
            int consumed = consumedSeatsByFlight.getOrDefault(flight.getFlightId(), 0);
            int recalculatedAvailability = Math.max(flight.getTotalSeats() - consumed, 0);
            if (recalculatedAvailability != flight.getAvailableSeats()) {
                flight.setAvailableSeats(recalculatedAvailability);
                dirty = true;
            }
        }

        if (dirty) {
            flightRepository.saveAll(flights);
        }

        lastSyncAt = Instant.now();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchBookings() {
        try {
            List<Map<String, Object>> bookings = restTemplate.getForObject(bookingBaseUrl + "/api/v1/bookings", List.class);
            return bookings == null ? List.of() : bookings;
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private boolean isSeatConsumingStatus(Object status) {
        if (!(status instanceof String value)) {
            return false;
        }
        return "CONFIRMED".equalsIgnoreCase(value)
            || "COMPLETED".equalsIgnoreCase(value)
            || "NO_SHOW".equalsIgnoreCase(value);
    }

    private int extractSeatCount(Object seatIds) {
        if (seatIds instanceof List<?> seats) {
            return (int) seats.stream().filter(item -> item != null).count();
        }
        return 0;
    }

    private Long toLong(Object rawValue) {
        if (rawValue instanceof Number number) {
            return number.longValue();
        }
        try {
            return rawValue == null ? null : Long.valueOf(String.valueOf(rawValue));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
