package com.moviebooking.booking;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response payloads for seat holds and bookings. */
public class BookingDtos {

    // ----- Hold -----
    public record HoldRequest(
            @NotNull Long showId,
            @NotEmpty List<Long> showSeatIds) {
    }

    public record HeldSeat(Long showSeatId, String seatLabel, BigDecimal price) {
    }

    public record HoldResponse(Long holdId, Long showId, String status, LocalDateTime expiresAt,
                               List<HeldSeat> seats, BigDecimal subtotal) {
    }

    // ----- Booking -----
    public record BookingRequest(
            @NotNull Long holdId,
            String discountCode) {
    }

    public record BookingResponse(String bookingRef, Long showId, String movieTitle, String status,
                                  List<HeldSeat> seats, BigDecimal subtotal, BigDecimal discountAmount,
                                  BigDecimal totalAmount, String paymentStatus, LocalDateTime createdAt) {
    }
}
