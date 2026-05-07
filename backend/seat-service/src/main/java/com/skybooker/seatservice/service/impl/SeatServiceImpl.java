package com.skybooker.seatservice.service.impl;

import com.skybooker.seatservice.dto.ConfirmSeatRequest;
import com.skybooker.seatservice.dto.HoldSeatRequest;
import com.skybooker.seatservice.dto.ReleaseSeatRequest;
import com.skybooker.seatservice.dto.SeatActionResponse;
import com.skybooker.seatservice.dto.SeatMapResponse;
import com.skybooker.seatservice.dto.SeatResponse;
import com.skybooker.seatservice.entity.Seat;
import com.skybooker.seatservice.entity.SeatClass;
import com.skybooker.seatservice.entity.SeatStatus;
import com.skybooker.seatservice.exception.BadRequestException;
import com.skybooker.seatservice.exception.ConflictException;
import com.skybooker.seatservice.exception.ResourceNotFoundException;
import com.skybooker.seatservice.repository.SeatRepository;
import com.skybooker.seatservice.service.SeatService;
import jakarta.persistence.OptimisticLockException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implements seat map lifecycle with optimistic locking and hold expiry.
 */
@Service
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final long holdDurationMinutes;
    private final boolean paymentConfirmationEnabled;
    private final int defaultTotalSeats;

    public SeatServiceImpl(SeatRepository seatRepository,
                           @Value("${seat.hold-duration-minutes:15}") long holdDurationMinutes,
                           @Value("${seat.payment-confirmation-enabled:false}") boolean paymentConfirmationEnabled,
                           @Value("${seat.default-total-seats:180}") int defaultTotalSeats) {
        this.seatRepository = seatRepository;
        this.holdDurationMinutes = holdDurationMinutes;
        this.paymentConfirmationEnabled = paymentConfirmationEnabled;
        this.defaultTotalSeats = defaultTotalSeats;
    }

    @Override
    @Transactional
    public SeatMapResponse getSeatMap(Long flightId, Integer totalSeats) {
        releaseExpiredHoldsForFlight(flightId);
        releaseConfirmedSeatsForFlightWhenPaymentDisabled(flightId);
        int resolvedTotalSeats = resolveTotalSeats(totalSeats);

        List<Seat> seats = seatRepository.findByFlightIdOrderByRowAscColumnAsc(flightId);
        if (seats.isEmpty()) {
            try {
                seats = seatRepository.saveAll(generateDefaultSeatLayout(flightId, resolvedTotalSeats));
            } catch (DataIntegrityViolationException exception) {
                // Another request created the initial layout first; fetch the persisted map.
                seats = seatRepository.findByFlightIdOrderByRowAscColumnAsc(flightId);
            }
            seats.sort(Comparator.comparing(Seat::getRow).thenComparing(Seat::getColumn));
        } else if (isLegacyLayout(seats, resolvedTotalSeats)) {
            seatRepository.deleteAllInBatch(seats);
            seats = seatRepository.saveAll(generateDefaultSeatLayout(flightId, resolvedTotalSeats));
            seats.sort(Comparator.comparing(Seat::getRow).thenComparing(Seat::getColumn));
        }

        SeatMapResponse response = new SeatMapResponse();
        response.setFlightId(flightId);
        response.setSeats(seats.stream().map(this::toResponse).toList());
        response.setAvailableSeatsByClass(countAvailableSeatsByClass(flightId));
        return response;
    }

    @Override
    @Transactional
    public SeatActionResponse holdSeats(HoldSeatRequest request) {
        List<Seat> seats = getSeatsForAction(request.getFlightId(), request.getSeatIds());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime holdUntil = now.plusMinutes(holdDurationMinutes);

        for (Seat seat : seats) {
            if (seat.getStatus() == SeatStatus.BLOCKED || seat.getStatus() == SeatStatus.CONFIRMED) {
                throw new ConflictException("Seat " + seat.getSeatNumber() + " is not available");
            }

            if (seat.getStatus() == SeatStatus.HELD && seat.getHoldExpiresAt() != null && seat.getHoldExpiresAt().isAfter(now)) {
                throw new ConflictException("Seat " + seat.getSeatNumber() + " was just taken");
            }

            seat.setStatus(SeatStatus.HELD);
            seat.setHoldExpiresAt(holdUntil);
            saveWithOptimisticLock(seat);
        }

        return new SeatActionResponse("Seats held successfully", request.getFlightId(), request.getSeatIds(), holdUntil);
    }

    @Override
    @Transactional
    public SeatActionResponse releaseSeats(ReleaseSeatRequest request) {
        List<Seat> seats = getSeatsForAction(request.getFlightId(), request.getSeatIds());

        for (Seat seat : seats) {
            if (seat.getStatus() == SeatStatus.BLOCKED) {
                throw new BadRequestException("Seat " + seat.getSeatNumber() + " cannot be released");
            }

            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHoldExpiresAt(null);
            saveWithOptimisticLock(seat);
        }

        return new SeatActionResponse("Seats released successfully", request.getFlightId(), request.getSeatIds(), null);
    }

    @Override
    @Transactional
    public SeatActionResponse confirmSeats(ConfirmSeatRequest request) {
        List<Seat> seats = getSeatsForAction(request.getFlightId(), request.getSeatIds());
        LocalDateTime now = LocalDateTime.now();

        if (!paymentConfirmationEnabled) {
            for (Seat seat : seats) {
                if (seat.getStatus() != SeatStatus.HELD) {
                    throw new ConflictException("Seat " + seat.getSeatNumber() + " is not on hold");
                }

                if (seat.getHoldExpiresAt() == null || !seat.getHoldExpiresAt().isAfter(now)) {
                    throw new ConflictException("Seat hold has expired for " + seat.getSeatNumber());
                }

                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHoldExpiresAt(null);
                saveWithOptimisticLock(seat);
            }

            return new SeatActionResponse(
                "Payment service is not integrated yet. Seats were released and not booked.",
                request.getFlightId(),
                request.getSeatIds(),
                null
            );
        }

        for (Seat seat : seats) {
            if (seat.getStatus() != SeatStatus.HELD) {
                throw new ConflictException("Seat " + seat.getSeatNumber() + " is not on hold");
            }

            if (seat.getHoldExpiresAt() == null || !seat.getHoldExpiresAt().isAfter(now)) {
                throw new ConflictException("Seat hold has expired for " + seat.getSeatNumber());
            }

            seat.setStatus(SeatStatus.CONFIRMED);
            seat.setHoldExpiresAt(null);
            saveWithOptimisticLock(seat);
        }

        return new SeatActionResponse("Seats confirmed successfully", request.getFlightId(), request.getSeatIds(), null);
    }

    @Override
    @Scheduled(fixedDelayString = "${seat.expiry-check-ms:30000}")
    @Transactional
    public void releaseExpiredHolds() {
        LocalDateTime now = LocalDateTime.now();
        List<Seat> expired = seatRepository.findByStatusAndHoldExpiresAtBefore(SeatStatus.HELD, now);
        for (Seat seat : expired) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHoldExpiresAt(null);
        }
        seatRepository.saveAll(expired);
    }

    private void releaseExpiredHoldsForFlight(Long flightId) {
        LocalDateTime now = LocalDateTime.now();
        List<Seat> expired = seatRepository.findExpiredHoldsByFlightId(flightId, SeatStatus.HELD, now);
        for (Seat seat : expired) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHoldExpiresAt(null);
        }
        seatRepository.saveAll(expired);
    }

    private void releaseConfirmedSeatsForFlightWhenPaymentDisabled(Long flightId) {
        if (paymentConfirmationEnabled) {
            return;
        }

        List<Seat> confirmedSeats = seatRepository.findByFlightIdAndStatus(flightId, SeatStatus.CONFIRMED);
        if (confirmedSeats.isEmpty()) {
            return;
        }

        for (Seat seat : confirmedSeats) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHoldExpiresAt(null);
        }
        seatRepository.saveAll(confirmedSeats);
    }

    private Map<SeatClass, Long> countAvailableSeatsByClass(Long flightId) {
        Map<SeatClass, Long> counts = new EnumMap<>(SeatClass.class);
        for (SeatClass seatClass : SeatClass.values()) {
            counts.put(seatClass, 0L);
        }

        seatRepository.countByFlightAndStatusGroupedByClass(flightId, SeatStatus.AVAILABLE)
            .forEach(row -> counts.put(row.getSeatClass(), row.getCount()));

        return counts;
    }

    private List<Seat> getSeatsForAction(Long flightId, List<Long> seatIds) {
        Set<Long> uniqueSeatIds = new HashSet<>(seatIds);
        if (uniqueSeatIds.size() != seatIds.size()) {
            throw new BadRequestException("Duplicate seat IDs are not allowed");
        }

        List<Seat> seats = seatRepository.findBySeatIdInAndFlightId(seatIds, flightId);
        if (seats.size() != seatIds.size()) {
            throw new ResourceNotFoundException("Some seats were not found for flight " + flightId);
        }

        return seats;
    }

    private void saveWithOptimisticLock(Seat seat) {
        try {
            seatRepository.saveAndFlush(seat);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw new ConflictException("Seat was updated by another request. Please retry");
        }
    }

    private SeatResponse toResponse(Seat seat) {
        SeatResponse response = new SeatResponse();
        response.setSeatId(seat.getSeatId());
        response.setFlightId(seat.getFlightId());
        response.setSeatNumber(seat.getSeatNumber());
        response.setSeatClass(seat.getSeatClass());
        response.setRow(seat.getRow());
        response.setColumn(seat.getColumn());
        response.setWindow(seat.isWindow());
        response.setAisle(seat.isAisle());
        response.setHasExtraLegroom(seat.isHasExtraLegroom());
        response.setStatus(seat.getStatus());
        response.setPriceMultiplier(seat.getPriceMultiplier());
        response.setHoldExpiresAt(seat.getHoldExpiresAt());
        return response;
    }

    private List<Seat> generateDefaultSeatLayout(Long flightId, int totalSeats) {
        List<Seat> seats = new ArrayList<>();
        int remainingSeats = Math.max(40, totalSeats);
        int row = 1;
        while (remainingSeats > 0) {
            SeatClass seatClass = resolveSeatClassByRow(row);
            List<String> columns = resolveColumnsBySeatClass(seatClass);
            BigDecimal multiplier = seatClass == SeatClass.FIRST
                ? new BigDecimal("2.50")
                : (seatClass == SeatClass.BUSINESS ? new BigDecimal("1.80") : new BigDecimal("1.00"));

            for (String col : columns) {
                if (remainingSeats <= 0) {
                    break;
                }

                Seat seat = new Seat();
                seat.setFlightId(flightId);
                seat.setSeatNumber(row + col);
                seat.setSeatClass(seatClass);
                seat.setRow(row);
                seat.setColumn(col);
                seat.setWindow(isWindowSeat(col, seatClass));
                seat.setAisle(isAisleSeat(col, seatClass));
                seat.setHasExtraLegroom(false);
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHoldExpiresAt(null);
                seat.setPriceMultiplier(multiplier);
                seats.add(seat);
                remainingSeats--;
            }

            row++;
        }

        applyExtraLegroomFlags(seats);

        return seats;
    }

    private void applyExtraLegroomFlags(List<Seat> seats) {
        int firstStartRow = 1;
        int businessStartRow = 4;
        int economyStartRow = 9;
        Set<Integer> extraLegroomRows = Set.of(
            firstStartRow,
            businessStartRow,
            economyStartRow,
            economyStartRow + 6
        );

        for (Seat seat : seats) {
            boolean hasExtraLegroom = extraLegroomRows.contains(seat.getRow());
            seat.setHasExtraLegroom(hasExtraLegroom);
            seat.setPriceMultiplier(withLegroomMultiplier(seat.getPriceMultiplier(), hasExtraLegroom));
        }
    }

    private boolean isLegacyLayout(List<Seat> seats, int targetTotalSeats) {
        Set<String> validColumns = Set.of("A", "B", "C", "D", "E", "F", "G", "H", "I");
        boolean hasInvalidColumns = seats.stream()
            .map(Seat::getColumn)
            .anyMatch(column -> !validColumns.contains(column));

        if (hasInvalidColumns) {
            return true;
        }

        boolean firstLayoutMismatch = seats.stream()
            .filter(seat -> seat.getSeatClass() == SeatClass.FIRST)
            .anyMatch(seat -> !Set.of("A", "D", "G").contains(seat.getColumn()) || seat.getRow() > 3);
        boolean businessLayoutMismatch = seats.stream()
            .filter(seat -> seat.getSeatClass() == SeatClass.BUSINESS)
            .anyMatch(seat -> !Set.of("A", "B", "D", "E", "G", "H").contains(seat.getColumn())
                || seat.getRow() < 4 || seat.getRow() > 8);
        boolean economyLayoutMismatch = seats.stream()
            .filter(seat -> seat.getSeatClass() == SeatClass.ECONOMY)
            .anyMatch(seat -> !Set.of("A", "B", "C", "D", "E", "F", "G", "H", "I").contains(seat.getColumn())
                || seat.getRow() < 9);

        if (firstLayoutMismatch || businessLayoutMismatch || economyLayoutMismatch) {
            return true;
        }

        boolean hasLockedSeats = seats.stream()
            .anyMatch(seat -> seat.getStatus() == SeatStatus.HELD || seat.getStatus() == SeatStatus.CONFIRMED);
        if (!hasLockedSeats && Math.abs(seats.size() - targetTotalSeats) >= 9) {
            return true;
        }

        return seats.size() < Math.max(40, targetTotalSeats / 2);
    }

    private int resolveTotalSeats(Integer totalSeats) {
        if (totalSeats == null || totalSeats < 40) {
            return defaultTotalSeats;
        }
        return totalSeats;
    }

    private SeatClass resolveSeatClassByRow(int row) {
        if (row <= 3) {
            return SeatClass.FIRST;
        }
        if (row <= 8) {
            return SeatClass.BUSINESS;
        }
        return SeatClass.ECONOMY;
    }

    private List<String> resolveColumnsBySeatClass(SeatClass seatClass) {
        if (seatClass == SeatClass.FIRST) {
            return List.of("A", "D", "G");
        }
        if (seatClass == SeatClass.BUSINESS) {
            return List.of("A", "B", "D", "E", "G", "H");
        }
        return List.of("A", "B", "C", "D", "E", "F", "G", "H", "I");
    }

    private boolean isWindowSeat(String column, SeatClass seatClass) {
        if (seatClass == SeatClass.FIRST) {
            return "A".equals(column) || "G".equals(column);
        }
        if (seatClass == SeatClass.BUSINESS) {
            return "A".equals(column) || "H".equals(column);
        }
        return "A".equals(column) || "I".equals(column);
    }

    private boolean isAisleSeat(String column, SeatClass seatClass) {
        if (seatClass == SeatClass.FIRST) {
            return true;
        }
        if (seatClass == SeatClass.BUSINESS) {
            return "B".equals(column) || "D".equals(column) || "E".equals(column) || "G".equals(column);
        }
        return "C".equals(column) || "D".equals(column) || "F".equals(column) || "G".equals(column);
    }

    private BigDecimal withLegroomMultiplier(BigDecimal base, boolean extraLegroom) {
        if (!extraLegroom) {
            return base;
        }
        return base.add(new BigDecimal("0.20"));
    }
}
