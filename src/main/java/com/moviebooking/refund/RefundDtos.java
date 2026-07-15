package com.moviebooking.refund;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Request/response payloads for refund policy rules and cancellations. */
public class RefundDtos {

    public record RefundRuleRequest(
            @NotNull @Min(0) Integer minHoursBeforeShow,
            @NotNull @Min(0) @Max(100) Integer refundPercent) {
    }

    public record RefundRuleResponse(Long id, Integer minHoursBeforeShow, Integer refundPercent) {
    }

    /** Returned when a booking is cancelled: how much is refunded and under which policy tier. */
    public record CancellationResponse(String bookingRef, String status,
                                       int refundPercent, BigDecimal refundAmount) {
    }
}
