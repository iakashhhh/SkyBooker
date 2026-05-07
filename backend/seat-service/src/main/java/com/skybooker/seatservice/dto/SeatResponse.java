package com.skybooker.seatservice.dto;

import com.skybooker.seatservice.entity.SeatClass;
import com.skybooker.seatservice.entity.SeatStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Seat view model returned to clients.
 */
@Data
@NoArgsConstructor
public class SeatResponse {

    private Long seatId;
    private Long flightId;
    private String seatNumber;
    private SeatClass seatClass;
    private Integer row;
    private String column;
    private boolean isWindow;
    private boolean isAisle;
    private boolean hasExtraLegroom;
    private SeatStatus status;
    private BigDecimal priceMultiplier;
    private LocalDateTime holdExpiresAt;
}
