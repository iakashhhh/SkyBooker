package com.skybooker.adminserver.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAggregationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAggregationController.class);

    private final RestTemplate restTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final String authBaseUrl;
    private final String bookingBaseUrl;
    private final String paymentBaseUrl;
    private final String airlineAirportBaseUrl;
    private final String flightBaseUrl;
    private final String passengerBaseUrl;
    private final String notificationExchange;
    private final String flightDelayedRoutingKey;
    private final String flightCancelledRoutingKey;
    private final String bookingCancelledRoutingKey;

    public AdminAggregationController(RestTemplate restTemplate,
                                      RabbitTemplate rabbitTemplate,
                                      @Value("${admin.upstream.auth-base-url}") String authBaseUrl,
                                      @Value("${admin.upstream.booking-base-url}") String bookingBaseUrl,
                                      @Value("${admin.upstream.payment-base-url}") String paymentBaseUrl,
                                      @Value("${admin.upstream.airline-airport-base-url}") String airlineAirportBaseUrl,
                                      @Value("${admin.upstream.flight-base-url:http://localhost:8080}") String flightBaseUrl,
                                      @Value("${admin.upstream.passenger-base-url:http://localhost:8080}") String passengerBaseUrl,
                                      @Value("${admin.notification.exchange:skybooker.events}") String notificationExchange,
                                      @Value("${admin.notification.routing.flight-delayed:flight.delayed}") String flightDelayedRoutingKey,
                                      @Value("${admin.notification.routing.flight-cancelled:flight.cancelled}") String flightCancelledRoutingKey,
                                      @Value("${admin.notification.routing.booking-cancelled:booking.cancelled}") String bookingCancelledRoutingKey) {
        this.restTemplate = restTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.authBaseUrl = authBaseUrl;
        this.bookingBaseUrl = bookingBaseUrl;
        this.paymentBaseUrl = paymentBaseUrl;
        this.airlineAirportBaseUrl = airlineAirportBaseUrl;
        this.flightBaseUrl = flightBaseUrl;
        this.passengerBaseUrl = passengerBaseUrl;
        this.notificationExchange = notificationExchange;
        this.flightDelayedRoutingKey = flightDelayedRoutingKey;
        this.flightCancelledRoutingKey = flightCancelledRoutingKey;
        this.bookingCancelledRoutingKey = bookingCancelledRoutingKey;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        AccessContext context = resolveAccessContext(authHeader);
        requireAdmin(context);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        return exchangeList(authBaseUrl + "/auth/users", new HttpEntity<>(headers));
    }

    @GetMapping("/bookings")
    public List<Map<String, Object>> bookings(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        AccessContext context = resolveAccessContext(authHeader);
        requireAdmin(context);
        return getList(bookingBaseUrl + "/api/v1/bookings");
    }

    @GetMapping("/operations/bookings")
    public List<Map<String, Object>> managedBookings(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        AccessContext context = resolveAccessContext(authHeader);
        requireOperationsUser(context);
        return getManagedBookings(context);
    }

    @GetMapping("/operations/passengers")
    public List<Map<String, Object>> managedPassengers(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        AccessContext context = resolveAccessContext(authHeader);
        requireOperationsUser(context);

        List<Map<String, Object>> passengers = new ArrayList<>();
        for (Map<String, Object> booking : getManagedBookings(context)) {
            Object bookingId = booking.get("bookingId");
            if (bookingId == null) {
                continue;
            }
            passengers.addAll(getList(passengerBaseUrl + "/api/v1/passengers/booking/" + bookingId));
        }

        return passengers;
    }

    @GetMapping("/payments")
    public List<Map<String, Object>> payments(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        AccessContext context = resolveAccessContext(authHeader);
        requireAdmin(context);
        return getList(paymentBaseUrl + "/payments");
    }

    @GetMapping("/analytics")
    public Map<String, Object> analytics(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        AccessContext context = resolveAccessContext(authHeader);
        requireAdmin(context);

        List<Map<String, Object>> bookings = getList(bookingBaseUrl + "/api/v1/bookings");
        List<Map<String, Object>> payments = getList(paymentBaseUrl + "/payments");
        Map<String, Object> revenue = getMap(
            paymentBaseUrl + "/payments/revenue?fromDate=" + LocalDate.now().minusDays(30) + "&toDate=" + LocalDate.now()
        );

        List<Map<String, Object>> airlines = getList(airlineAirportBaseUrl + "/airlines");
        List<Map<String, Object>> airports = getList(airlineAirportBaseUrl + "/airports");

        return Map.of(
            "bookingsCount", bookings.size(),
            "paymentsCount", payments.size(),
            "revenue", revenue.getOrDefault("revenue", BigDecimal.ZERO),
            "airlinesCount", airlines.size(),
            "airportsCount", airports.size()
        );
    }

    @GetMapping("/flights")
    public List<Map<String, Object>> flights(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) Long airlineId) {
        AccessContext context = resolveAccessContext(authHeader);
        requireOperationsUser(context);

        Long effectiveAirlineId = context.isStaff() ? context.airlineId() : airlineId;
        StringBuilder url = new StringBuilder(flightBaseUrl).append("/flights");
        List<String> queryParts = new ArrayList<>();
        if (date != null) {
            queryParts.add("date=" + date);
        }
        if (effectiveAirlineId != null) {
            queryParts.add("airlineId=" + effectiveAirlineId);
        }
        if (!queryParts.isEmpty()) {
            url.append("?").append(String.join("&", queryParts));
        }

        return getList(url.toString());
    }

    @PostMapping("/flights")
    public Map<String, Object> createFlight(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                             @RequestBody Map<String, Object> request) {
        AccessContext context = resolveAccessContext(authHeader);
        requireOperationsUser(context);

        Long requestedAirlineId = toLong(request.get("airlineId"));
        Long effectiveAirlineId = resolveManagedAirlineId(context, requestedAirlineId);
        request.put("airlineId", effectiveAirlineId);

        return postMap(flightBaseUrl + "/flights", request);
    }

    @PutMapping("/flights/{flightId}")
    public Map<String, Object> updateFlight(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                            @PathVariable Long flightId,
                                            @RequestBody Map<String, Object> request) {
        AccessContext context = resolveAccessContext(authHeader);
        requireOperationsUser(context);
        assertFlightAccess(context, flightId);

        Long requestedAirlineId = toLong(request.get("airlineId"));
        Long effectiveAirlineId = resolveManagedAirlineId(context, requestedAirlineId);
        request.put("airlineId", effectiveAirlineId);

        return putMap(flightBaseUrl + "/flights/" + flightId, request);
    }

    @DeleteMapping("/flights/{flightId}")
    public ResponseEntity<Void> deleteFlight(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                             @PathVariable Long flightId) {
        AccessContext context = resolveAccessContext(authHeader);
        requireOperationsUser(context);
        assertFlightAccess(context, flightId);
        if (context.isStaff()) {
            throw new ResponseStatusException(FORBIDDEN, "Only admin can delete flights");
        }

        restTemplate.exchange(flightBaseUrl + "/flights/" + flightId, HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/flights/{flightId}/cancel")
    public Map<String, Object> cancelFlight(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                            @PathVariable Long flightId) {
        AccessContext context = resolveAccessContext(authHeader);
        requireOperationsUser(context);
        assertFlightAccess(context, flightId);
        Map<String, Object> previousFlight = getMap(flightBaseUrl + "/flights/" + flightId);
        putMap(flightBaseUrl + "/flights/status", Map.of("flightId", flightId, "status", "CANCELLED"));

        List<Map<String, Object>> affectedBookings = bookingsByFlight(authHeader, flightId)
            .stream()
            .filter(booking -> "CONFIRMED".equalsIgnoreCase(String.valueOf(booking.get("status"))))
            .toList();

        int cancelledCount = 0;
        for (Map<String, Object> booking : affectedBookings) {
            String bookingId = String.valueOf(booking.get("bookingId"));
            if (bookingId == null || bookingId.isBlank() || "null".equalsIgnoreCase(bookingId)) {
                continue;
            }
            putMap(bookingBaseUrl + "/api/v1/bookings/cancel", Map.of("bookingId", bookingId));
            clearPassengerSeatsForBooking(bookingId);
            publishFlightEvent(flightCancelledRoutingKey, "FLIGHT_CANCELLED", previousFlight, booking, Map.of());
            cancelledCount++;
        }

        Map<String, Object> response = new LinkedHashMap<>(getMap(flightBaseUrl + "/flights/" + flightId));
        response.put("affectedBookings", cancelledCount);
        return response;
    }

    @PutMapping("/flights/{flightId}/delay")
    public Map<String, Object> delayFlight(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                           @PathVariable Long flightId,
                                           @RequestBody Map<String, Object> request) {
        AccessContext context = resolveAccessContext(authHeader);
        requireOperationsUser(context);
        assertFlightAccess(context, flightId);

        String newDeparture = String.valueOf(request.getOrDefault("newEstimatedDepartureTime", "")).trim();
        if (newDeparture.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "newEstimatedDepartureTime is required");
        }

        Map<String, Object> existingFlight = getMap(flightBaseUrl + "/flights/" + flightId);
        Map<String, Object> updatePayload = new LinkedHashMap<>();
        updatePayload.put("flightNumber", existingFlight.get("flightNumber"));
        updatePayload.put("airlineId", existingFlight.get("airlineId"));
        updatePayload.put("originAirportCode", existingFlight.get("originAirportCode"));
        updatePayload.put("destinationAirportCode", existingFlight.get("destinationAirportCode"));
        updatePayload.put("departureTime", newDeparture);
        updatePayload.put("arrivalTime", existingFlight.get("arrivalTime"));
        updatePayload.put("durationMinutes", existingFlight.get("durationMinutes"));
        updatePayload.put("numberOfStops", existingFlight.get("numberOfStops"));
        updatePayload.put("viaAirportCode", existingFlight.get("viaAirportCode"));
        updatePayload.put("status", "DELAYED");
        updatePayload.put("aircraftType", existingFlight.get("aircraftType"));
        updatePayload.put("totalSeats", existingFlight.get("totalSeats"));
        updatePayload.put("availableSeats", existingFlight.get("availableSeats"));
        updatePayload.put("basePrice", existingFlight.get("basePrice"));

        Map<String, Object> updatedFlight = putMap(flightBaseUrl + "/flights/" + flightId, updatePayload);
        updatedFlight.put("delayReason", request.getOrDefault("delayReason", "Other"));
        updatedFlight.put("internalNotes", request.getOrDefault("internalNotes", ""));
        updatedFlight.put("originalDepartureTime", existingFlight.get("departureTime"));
        List<Map<String, Object>> impactedBookings = bookingsByFlight(authHeader, flightId)
            .stream()
            .filter(booking -> "CONFIRMED".equalsIgnoreCase(String.valueOf(booking.get("status"))))
            .toList();
        for (Map<String, Object> booking : impactedBookings) {
            tryAppendBookingDelayEvent(booking, updatedFlight, request);
            publishFlightEvent(flightDelayedRoutingKey, "FLIGHT_DELAYED", updatedFlight, booking, request);
        }
        updatedFlight.put("affectedBookings", impactedBookings.size());
        return updatedFlight;
    }

    @PutMapping("/bookings/{bookingId}/cancel")
    public Map<String, Object> cancelManagedBooking(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                                    @PathVariable String bookingId,
                                                    @RequestBody(required = false) Map<String, Object> request) {
        AccessContext context = resolveAccessContext(authHeader);
        requireOperationsUser(context);

        Map<String, Object> booking = getMap(bookingBaseUrl + "/api/v1/bookings/" + bookingId);
        Long flightId = toLong(booking.get("flightId"));
        if (flightId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Booking has no linked flight");
        }
        assertFlightAccess(context, flightId);

        Map<String, Object> cancelled = putMap(bookingBaseUrl + "/api/v1/bookings/cancel", Map.of("bookingId", bookingId));
        clearPassengerSeatsForBooking(bookingId);
        if (request != null && request.containsKey("reason")) {
            cancelled.put("reason", request.get("reason"));
        }
        publishBookingCancelledEvent(booking, request, context.userId());
        return cancelled;
    }

    @GetMapping("/flights/dashboard")
    public Map<String, Object> flightDashboard(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        AccessContext context = resolveAccessContext(authHeader);
        requireOperationsUser(context);

        List<Map<String, Object>> flights = context.isStaff()
            ? getList(flightBaseUrl + "/flights?airlineId=" + context.airlineId())
            : getList(flightBaseUrl + "/flights");
        List<Map<String, Object>> bookings = getList(bookingBaseUrl + "/api/v1/bookings");

        Set<Long> managedFlightIds = flights.stream()
            .map(flight -> toLong(flight.get("flightId")))
            .filter(id -> id != null)
            .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        long todayFlights = flights.stream().filter(this::isTodayFlight).count();
        long managedBookings = bookings.stream()
            .map(booking -> toLong(booking.get("flightId")))
            .filter(flightId -> flightId != null && managedFlightIds.contains(flightId))
            .count();

        return Map.of(
            "totalFlights", flights.size(),
            "todayFlights", todayFlights,
            "bookingsCount", managedBookings
        );
    }

    @GetMapping("/flights/{flightId}/bookings")
    public List<Map<String, Object>> bookingsByFlight(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                                       @PathVariable Long flightId) {
        AccessContext context = resolveAccessContext(authHeader);
        requireOperationsUser(context);
        assertFlightAccess(context, flightId);
        return getManagedBookings(context)
            .stream()
            .filter(booking -> flightId.equals(toLong(booking.get("flightId"))))
            .toList();
    }

    @GetMapping("/flights/{flightId}/passengers")
    public List<Map<String, Object>> passengersByFlight(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                                         @PathVariable Long flightId) {
        AccessContext context = resolveAccessContext(authHeader);
        requireOperationsUser(context);
        assertFlightAccess(context, flightId);

        List<Map<String, Object>> passengers = new ArrayList<>();
        List<Map<String, Object>> flightBookings = bookingsByFlight(authHeader, flightId);

        for (Map<String, Object> booking : flightBookings) {
            Object bookingId = booking.get("bookingId");
            if (bookingId == null) {
                continue;
            }
            passengers.addAll(getList(passengerBaseUrl + "/api/v1/passengers/booking/" + bookingId));
        }

        return passengers;
    }

    @PutMapping("/staff-airlines/{userId}")
    public Map<String, Object> assignStaffAirline(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                                   @PathVariable Long userId,
                                                   @RequestBody Map<String, Object> request) {
        AccessContext context = resolveAccessContext(authHeader);
        requireAdmin(context);
        return putMap(airlineAirportBaseUrl + "/staff-airlines/" + userId, request);
    }

    @GetMapping("/staff-airlines/{userId}")
    public Map<String, Object> getStaffAirline(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                                @PathVariable Long userId) {
        AccessContext context = resolveAccessContext(authHeader);
        if (context.isStaff() && !context.userId().equals(userId)) {
            throw new ResponseStatusException(FORBIDDEN, "Airline staff can only access their own mapping");
        }
        return getMap(airlineAirportBaseUrl + "/staff-airlines/" + userId);
    }

    private void requireOperationsUser(AccessContext context) {
        if (!"ADMIN".equals(context.role()) && !"AIRLINE_STAFF".equals(context.role())) {
            throw new ResponseStatusException(FORBIDDEN, "Operations access requires admin or airline staff role");
        }
    }

    private void requireAdmin(AccessContext context) {
        if (!"ADMIN".equals(context.role())) {
            throw new ResponseStatusException(FORBIDDEN, "Admin access required");
        }
    }

    private Long resolveManagedAirlineId(AccessContext context, Long requestedAirlineId) {
        if (context.isStaff()) {
            if (requestedAirlineId != null && !requestedAirlineId.equals(context.airlineId())) {
                throw new ResponseStatusException(FORBIDDEN, "Cannot manage flights for another airline");
            }
            return context.airlineId();
        }

        if (requestedAirlineId == null) {
            throw new ResponseStatusException(FORBIDDEN, "airlineId is required");
        }
        return requestedAirlineId;
    }

    private void assertFlightAccess(AccessContext context, Long flightId) {
        if (!context.isStaff()) {
            return;
        }

        Map<String, Object> flight = getMap(flightBaseUrl + "/flights/" + flightId);
        Long airlineId = toLong(flight.get("airlineId"));
        if (airlineId == null || !airlineId.equals(context.airlineId())) {
            throw new ResponseStatusException(FORBIDDEN, "Cannot manage flights outside your assigned airline");
        }
    }

    private AccessContext resolveAccessContext(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authorization header is required");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);
            Map<String, Object> profile = getMap(authBaseUrl + "/auth/profile", new HttpEntity<>(headers));
            Long userId = toLong(profile.get("userId"));
            String role = String.valueOf(profile.getOrDefault("role", ""));
            if (userId == null || role.isBlank()) {
                throw new ResponseStatusException(UNAUTHORIZED, "Unable to resolve authenticated user");
            }

            Long assignedAirlineId = null;
            if ("AIRLINE_STAFF".equals(role)) {
                Map<String, Object> mapping = getMap(airlineAirportBaseUrl + "/staff-airlines/" + userId);
                assignedAirlineId = toLong(mapping.get("airlineId"));
                if (assignedAirlineId == null) {
                    throw new ResponseStatusException(FORBIDDEN, "No airline mapped for staff user");
                }
            }

            return new AccessContext(userId, role, assignedAirlineId);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(UNAUTHORIZED, "Unable to authenticate operations request");
        }
    }

    private List<Map<String, Object>> getList(String url) {
        return exchangeList(url, HttpEntity.EMPTY);
    }

    private List<Map<String, Object>> exchangeList(String url, HttpEntity<?> requestEntity) {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            requestEntity,
            new ParameterizedTypeReference<>() {
            }
        );
        return response.getBody() == null ? List.of() : response.getBody();
    }

    private Map<String, Object> getMap(String url) {
        return getMap(url, HttpEntity.EMPTY);
    }

    private Map<String, Object> getMap(String url, HttpEntity<?> requestEntity) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            requestEntity,
            new ParameterizedTypeReference<>() {
            }
        );
        return response.getBody() == null ? Map.of() : response.getBody();
    }

    private Map<String, Object> postMap(String url, Map<String, Object> request) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url,
            HttpMethod.POST,
            new HttpEntity<>(request),
            new ParameterizedTypeReference<>() {
            }
        );
        return response.getBody() == null ? Map.of() : response.getBody();
    }

    private Map<String, Object> putMap(String url, Map<String, Object> request) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url,
            HttpMethod.PUT,
            new HttpEntity<>(request),
            new ParameterizedTypeReference<>() {
            }
        );
        return response.getBody() == null ? Map.of() : response.getBody();
    }

    private boolean isTodayFlight(Map<String, Object> flight) {
        Object departureTime = flight.get("departureTime");
        if (!(departureTime instanceof String value)) {
            return false;
        }
        return value.startsWith(LocalDate.now().toString());
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<Map<String, Object>> getManagedBookings(AccessContext context) {
        List<Map<String, Object>> bookings = getList(bookingBaseUrl + "/api/v1/bookings");
        if (!context.isStaff()) {
            return bookings;
        }

        Set<Long> managedFlightIds = getList(flightBaseUrl + "/flights?airlineId=" + context.airlineId())
            .stream()
            .map(flight -> toLong(flight.get("flightId")))
            .filter(id -> id != null)
            .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        return bookings.stream()
            .filter(booking -> {
                Long flightId = toLong(booking.get("flightId"));
                return flightId != null && managedFlightIds.contains(flightId);
            })
            .toList();
    }

    private void publishFlightEvent(String routingKey,
                                    String eventName,
                                    Map<String, Object> flight,
                                    Map<String, Object> booking,
                                    Map<String, Object> extraFields) {
        Long recipientId = toLong(booking.get("userId"));
        if (recipientId == null || recipientId <= 0) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventName", eventName);
        payload.put("type", "FLIGHT");
        payload.put("recipientId", recipientId);
        payload.put("recipientEmail", booking.get("contactEmail"));
        payload.put("recipientPhone", booking.get("contactPhone"));
        payload.put("relatedBookingId", booking.get("bookingId"));
        payload.put("relatedFlightId", toLong(flight.get("flightId")));
        payload.put("pnrCode", booking.get("pnrCode"));
        payload.put("flightNumber", flight.get("flightNumber"));
        payload.put("routeFrom", flight.get("originAirportCode"));
        payload.put("routeTo", flight.get("destinationAirportCode"));
        payload.put("departureDate", String.valueOf(flight.get("departureTime")));
        payload.put("channels", List.of("APP", "EMAIL"));

        if ("FLIGHT_DELAYED".equals(eventName)) {
            Integer delayMinutes = calculateDelayMinutes(
                String.valueOf(flight.get("originalDepartureTime")),
                String.valueOf(flight.get("departureTime"))
            );
            payload.put("delayMinutes", delayMinutes == null ? 0 : delayMinutes);
            payload.put("delayReason", extraFields.getOrDefault("delayReason", "Other"));
            payload.put("title", "Flight delay update");
            payload.put(
                "message",
                "Flight " + safeText(flight.get("flightNumber"), "SkyBooker Flight")
                    + " (" + safeText(flight.get("originAirportCode"), "--")
                    + " -> " + safeText(flight.get("destinationAirportCode"), "--")
                    + ") is delayed. Updated departure: "
                    + safeText(flight.get("departureTime"), "please check app") + "."
            );
        } else if ("FLIGHT_CANCELLED".equals(eventName)) {
            payload.put("title", "Flight cancellation alert");
            payload.put(
                "message",
                "Flight " + safeText(flight.get("flightNumber"), "SkyBooker Flight")
                    + " (" + safeText(flight.get("originAirportCode"), "--")
                    + " -> " + safeText(flight.get("destinationAirportCode"), "--")
                    + ") has been cancelled. Please contact support for rebooking."
            );
        }

        publishNotificationEvent(routingKey, payload);
    }

    private void publishBookingCancelledEvent(Map<String, Object> booking,
                                              Map<String, Object> request,
                                              Long cancelledByUserId) {
        Long recipientId = toLong(booking.get("userId"));
        if (recipientId == null || recipientId <= 0) {
            return;
        }

        Map<String, Object> flight = Map.of();
        Long flightId = toLong(booking.get("flightId"));
        if (flightId != null) {
            flight = getMap(flightBaseUrl + "/flights/" + flightId);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventName", "BOOKING_CANCELLED");
        payload.put("type", "BOOKING");
        payload.put("recipientId", recipientId);
        payload.put("recipientEmail", booking.get("contactEmail"));
        payload.put("recipientPhone", booking.get("contactPhone"));
        payload.put("relatedBookingId", booking.get("bookingId"));
        payload.put("relatedFlightId", flightId);
        payload.put("pnrCode", booking.get("pnrCode"));
        payload.put("flightNumber", flight.get("flightNumber"));
        payload.put("routeFrom", flight.get("originAirportCode"));
        payload.put("routeTo", flight.get("destinationAirportCode"));
        payload.put("departureDate", String.valueOf(flight.get("departureTime")));
        payload.put("channels", List.of("APP", "EMAIL"));
        payload.put("title", "Booking cancelled");
        payload.put(
            "message",
            "Booking " + safeText(booking.get("pnrCode"), safeText(booking.get("bookingId"), "NA"))
                + " has been cancelled"
                + (request != null && request.get("reason") != null ? " (" + request.get("reason") + ")" : "")
                + "."
        );
        payload.put("cancelledBy", cancelledByUserId);

        publishNotificationEvent(bookingCancelledRoutingKey, payload);
    }

    private void publishNotificationEvent(String routingKey, Map<String, Object> payload) {
        try {
            rabbitTemplate.convertAndSend(notificationExchange, routingKey, payload);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                "Unable to publish operations event exchange={} routingKey={} reason={}",
                notificationExchange,
                routingKey,
                exception.getMessage()
            );
        }
    }

    private void clearPassengerSeatsForBooking(String bookingId) {
        if (bookingId == null || bookingId.isBlank() || "null".equalsIgnoreCase(bookingId)) {
            return;
        }
        try {
            restTemplate.exchange(
                passengerBaseUrl + "/api/v1/passengers/booking/" + bookingId + "/clear-seats",
                HttpMethod.PUT,
                HttpEntity.EMPTY,
                Void.class
            );
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to clear seat assignments for booking {}: {}", bookingId, exception.getMessage());
        }
    }

    private void tryAppendBookingDelayEvent(Map<String, Object> booking,
                                            Map<String, Object> delayedFlight,
                                            Map<String, Object> request) {
        String bookingId = String.valueOf(booking.get("bookingId"));
        if (bookingId == null || bookingId.isBlank() || "null".equalsIgnoreCase(bookingId)) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "FLIGHT_DELAYED");
        payload.put("flightId", toLong(delayedFlight.get("flightId")));
        payload.put("flightNumber", delayedFlight.get("flightNumber"));
        payload.put("routeFrom", delayedFlight.get("originAirportCode"));
        payload.put("routeTo", delayedFlight.get("destinationAirportCode"));
        payload.put("originalDepartureTime", delayedFlight.get("originalDepartureTime"));
        payload.put("newEstimatedDepartureTime", delayedFlight.get("departureTime"));
        payload.put("delayReason", request.getOrDefault("delayReason", "Other"));
        payload.put("internalNotes", request.getOrDefault("internalNotes", ""));
        payload.put("recordedAt", LocalDateTime.now().toString());

        try {
            postMap(bookingBaseUrl + "/api/v1/bookings/" + bookingId + "/events", payload);
        } catch (RuntimeException exception) {
            LOGGER.debug("Delay event log endpoint unavailable for booking {}: {}", bookingId, exception.getMessage());
        }
    }

    private Integer calculateDelayMinutes(String originalDeparture, String newDeparture) {
        LocalDateTime original = parseDateTime(originalDeparture);
        LocalDateTime updated = parseDateTime(newDeparture);
        if (original == null || updated == null) {
            return null;
        }
        return Math.max(0, (int) Duration.between(original, updated).toMinutes());
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String safeText(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? fallback : text;
    }

    private record AccessContext(Long userId, String role, Long airlineId) {
        boolean isStaff() {
            return "AIRLINE_STAFF".equals(role);
        }
    }
}
