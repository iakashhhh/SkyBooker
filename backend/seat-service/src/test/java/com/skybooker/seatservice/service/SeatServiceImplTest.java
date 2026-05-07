package com.skybooker.seatservice.service;

import com.skybooker.seatservice.dto.ConfirmSeatRequest;
import com.skybooker.seatservice.dto.HoldSeatRequest;
import com.skybooker.seatservice.dto.ReleaseSeatRequest;
import com.skybooker.seatservice.dto.SeatActionResponse;
import com.skybooker.seatservice.dto.SeatMapResponse;
import com.skybooker.seatservice.entity.Seat;
import com.skybooker.seatservice.entity.SeatClass;
import com.skybooker.seatservice.entity.SeatStatus;
import com.skybooker.seatservice.exception.BadRequestException;
import com.skybooker.seatservice.exception.ConflictException;
import com.skybooker.seatservice.exception.ResourceNotFoundException;
import com.skybooker.seatservice.repository.SeatRepository;
import com.skybooker.seatservice.service.impl.SeatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatServiceImplTest {

    @Mock
    private SeatRepository seatRepository;

    private SeatServiceImpl seatService;

    @BeforeEach
    void setUp() {
        seatService = new SeatServiceImpl(seatRepository, 15, false, 180);
    }

    @Test
    void getSeatMap_shouldGenerateLayoutWhenNoSeatsExist() {
        when(seatRepository.findExpiredHoldsByFlightId(anyLong(), any(SeatStatus.class), any(LocalDateTime.class)))
            .thenReturn(List.of());
        when(seatRepository.findByFlightIdAndStatus(44L, SeatStatus.CONFIRMED)).thenReturn(List.of());
        when(seatRepository.findByFlightIdOrderByRowAscColumnAsc(44L)).thenReturn(List.of());
        when(seatRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(seatRepository.countByFlightAndStatusGroupedByClass(44L, SeatStatus.AVAILABLE)).thenReturn(List.of());

        SeatMapResponse response = seatService.getSeatMap(44L, 60);

        assertEquals(44L, response.getFlightId());
        assertFalse(response.getSeats().isEmpty());
        assertTrue(response.getSeats().stream().anyMatch(seat -> seat.getSeatClass() == SeatClass.FIRST));
    }

    @Test
    void getSeatMap_shouldRebuildLegacyLayout() {
        Seat legacy = sampleSeat(1L, 44L, "1Z", SeatStatus.AVAILABLE);
        legacy.setColumn("Z");
        legacy.setSeatClass(SeatClass.ECONOMY);
        when(seatRepository.findExpiredHoldsByFlightId(anyLong(), any(SeatStatus.class), any(LocalDateTime.class)))
            .thenReturn(List.of());
        when(seatRepository.findByFlightIdAndStatus(44L, SeatStatus.CONFIRMED)).thenReturn(List.of());
        when(seatRepository.findByFlightIdOrderByRowAscColumnAsc(44L)).thenReturn(List.of(legacy));
        when(seatRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(seatRepository.countByFlightAndStatusGroupedByClass(44L, SeatStatus.AVAILABLE)).thenReturn(List.of());

        SeatMapResponse response = seatService.getSeatMap(44L, 180);

        verify(seatRepository).deleteAllInBatch(anyList());
        assertFalse(response.getSeats().isEmpty());
    }

    @Test
    void getSeatMap_shouldReleaseConfirmedSeatsWhenPaymentConfirmationDisabled() {
        Seat confirmed = sampleSeat(2L, 44L, "2A", SeatStatus.CONFIRMED);

        when(seatRepository.findExpiredHoldsByFlightId(anyLong(), any(SeatStatus.class), any(LocalDateTime.class)))
            .thenReturn(List.of());
        when(seatRepository.findByFlightIdAndStatus(44L, SeatStatus.CONFIRMED)).thenReturn(List.of(confirmed));
        when(seatRepository.findByFlightIdOrderByRowAscColumnAsc(44L)).thenReturn(List.of(sampleSeat(3L, 44L, "3A", SeatStatus.AVAILABLE)));
        when(seatRepository.countByFlightAndStatusGroupedByClass(44L, SeatStatus.AVAILABLE)).thenReturn(List.of());

        seatService.getSeatMap(44L, 180);

        verify(seatRepository, atLeastOnce()).saveAll(anyList());
        assertEquals(SeatStatus.AVAILABLE, confirmed.getStatus());
    }

    @Test
    void holdSeats_shouldSetStatusHeldAndExpiry() {
        Seat seat = sampleSeat(1L, 44L, "12A", SeatStatus.AVAILABLE);
        HoldSeatRequest request = new HoldSeatRequest();
        request.setFlightId(44L);
        request.setSeatIds(List.of(1L));

        when(seatRepository.findBySeatIdInAndFlightId(List.of(1L), 44L)).thenReturn(List.of(seat));
        when(seatRepository.saveAndFlush(any(Seat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SeatActionResponse response = seatService.holdSeats(request);

        assertEquals("Seats held successfully", response.getMessage());
        assertEquals(SeatStatus.HELD, seat.getStatus());
        assertTrue(seat.getHoldExpiresAt() != null && seat.getHoldExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void holdSeats_shouldRejectDuplicateSeatIds() {
        HoldSeatRequest request = new HoldSeatRequest();
        request.setFlightId(44L);
        request.setSeatIds(List.of(1L, 1L));

        assertThrows(BadRequestException.class, () -> seatService.holdSeats(request));
        verify(seatRepository, never()).findBySeatIdInAndFlightId(anyList(), any(Long.class));
    }

    @Test
    void holdSeats_shouldThrowWhenSeatAlreadyConfirmed() {
        Seat confirmedSeat = sampleSeat(4L, 44L, "15A", SeatStatus.CONFIRMED);
        HoldSeatRequest request = new HoldSeatRequest();
        request.setFlightId(44L);
        request.setSeatIds(List.of(4L));

        when(seatRepository.findBySeatIdInAndFlightId(List.of(4L), 44L)).thenReturn(List.of(confirmedSeat));

        assertThrows(ConflictException.class, () -> seatService.holdSeats(request));
    }

    @Test
    void holdSeats_shouldThrowWhenSeatAlreadyHeldAndNotExpired() {
        Seat heldSeat = sampleSeat(6L, 44L, "10B", SeatStatus.HELD);
        heldSeat.setHoldExpiresAt(LocalDateTime.now().plusMinutes(5));
        HoldSeatRequest request = new HoldSeatRequest();
        request.setFlightId(44L);
        request.setSeatIds(List.of(6L));

        when(seatRepository.findBySeatIdInAndFlightId(List.of(6L), 44L)).thenReturn(List.of(heldSeat));

        assertThrows(ConflictException.class, () -> seatService.holdSeats(request));
    }

    @Test
    void holdSeats_shouldThrowWhenSeatRecordMissing() {
        HoldSeatRequest request = new HoldSeatRequest();
        request.setFlightId(44L);
        request.setSeatIds(List.of(999L));

        when(seatRepository.findBySeatIdInAndFlightId(List.of(999L), 44L)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> seatService.holdSeats(request));
    }

    @Test
    void releaseSeats_shouldSetHeldSeatsToAvailable() {
        Seat heldSeat = sampleSeat(3L, 44L, "14C", SeatStatus.HELD);
        heldSeat.setHoldExpiresAt(LocalDateTime.now().plusMinutes(10));

        ReleaseSeatRequest request = new ReleaseSeatRequest();
        request.setFlightId(44L);
        request.setSeatIds(List.of(3L));

        when(seatRepository.findBySeatIdInAndFlightId(anyList(), any(Long.class))).thenReturn(List.of(heldSeat));
        when(seatRepository.saveAndFlush(any(Seat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SeatActionResponse response = seatService.releaseSeats(request);

        assertEquals("Seats released successfully", response.getMessage());
        assertEquals(SeatStatus.AVAILABLE, heldSeat.getStatus());
        assertNull(heldSeat.getHoldExpiresAt());
    }

    @Test
    void releaseSeats_shouldRejectBlockedSeat() {
        Seat blockedSeat = sampleSeat(5L, 44L, "18F", SeatStatus.BLOCKED);
        ReleaseSeatRequest request = new ReleaseSeatRequest();
        request.setFlightId(44L);
        request.setSeatIds(List.of(5L));

        when(seatRepository.findBySeatIdInAndFlightId(List.of(5L), 44L)).thenReturn(List.of(blockedSeat));

        assertThrows(BadRequestException.class, () -> seatService.releaseSeats(request));
    }

    @SuppressWarnings("unchecked")
    @Test
    void releaseExpiredHolds_shouldMoveExpiredHeldSeatsToAvailable() {
        Seat expired = sampleSeat(2L, 44L, "12B", SeatStatus.HELD);
        expired.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(seatRepository.findByStatusAndHoldExpiresAtBefore(any(SeatStatus.class), any(LocalDateTime.class)))
            .thenReturn(List.of(expired));

        seatService.releaseExpiredHolds();

        ArgumentCaptor<List<Seat>> captor = (ArgumentCaptor<List<Seat>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(seatRepository, times(1)).saveAll(captor.capture());
        Seat updated = captor.getValue().getFirst();
        assertEquals(SeatStatus.AVAILABLE, updated.getStatus());
        assertNull(updated.getHoldExpiresAt());
    }

    @Test
    void confirmSeats_shouldReleaseWhenPaymentFlowIsDisabled() {
        Seat held = sampleSeat(7L, 44L, "7A", SeatStatus.HELD);
        held.setHoldExpiresAt(LocalDateTime.now().plusMinutes(5));
        ConfirmSeatRequest request = new ConfirmSeatRequest();
        request.setFlightId(44L);
        request.setSeatIds(List.of(7L));

        when(seatRepository.findBySeatIdInAndFlightId(List.of(7L), 44L)).thenReturn(List.of(held));
        when(seatRepository.saveAndFlush(any(Seat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SeatActionResponse response = seatService.confirmSeats(request);

        assertTrue(response.getMessage().contains("not integrated yet"));
        assertEquals(SeatStatus.AVAILABLE, held.getStatus());
    }

    @Test
    void confirmSeats_shouldThrowWhenHoldExpired() {
        Seat held = sampleSeat(8L, 44L, "7B", SeatStatus.HELD);
        held.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1));
        ConfirmSeatRequest request = new ConfirmSeatRequest();
        request.setFlightId(44L);
        request.setSeatIds(List.of(8L));

        when(seatRepository.findBySeatIdInAndFlightId(List.of(8L), 44L)).thenReturn(List.of(held));

        assertThrows(ConflictException.class, () -> seatService.confirmSeats(request));
    }

    @Test
    void confirmSeats_shouldSetConfirmedWhenPaymentFlowIsEnabled() {
        SeatServiceImpl paymentEnabledService = new SeatServiceImpl(seatRepository, 15, true, 180);
        Seat held = sampleSeat(9L, 44L, "8A", SeatStatus.HELD);
        held.setHoldExpiresAt(LocalDateTime.now().plusMinutes(3));
        ConfirmSeatRequest request = new ConfirmSeatRequest();
        request.setFlightId(44L);
        request.setSeatIds(List.of(9L));

        when(seatRepository.findBySeatIdInAndFlightId(List.of(9L), 44L)).thenReturn(List.of(held));
        when(seatRepository.saveAndFlush(any(Seat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SeatActionResponse response = paymentEnabledService.confirmSeats(request);

        assertEquals("Seats confirmed successfully", response.getMessage());
        assertEquals(SeatStatus.CONFIRMED, held.getStatus());
        assertNull(held.getHoldExpiresAt());
    }

    private Seat sampleSeat(Long seatId, Long flightId, String seatNumber, SeatStatus status) {
        Seat seat = new Seat();
        seat.setSeatId(seatId);
        seat.setFlightId(flightId);
        seat.setSeatNumber(seatNumber);
        seat.setSeatClass(SeatClass.ECONOMY);
        seat.setRow(12);
        seat.setColumn("A");
        seat.setWindow(true);
        seat.setAisle(false);
        seat.setHasExtraLegroom(false);
        seat.setStatus(status);
        seat.setPriceMultiplier(BigDecimal.ONE);
        return seat;
    }
}
