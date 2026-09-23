package com.mayankbhati.movietickets.booking.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class BookingItemId implements Serializable {
    @Column(name = "booking_id", length = 36)
    private String bookingId;
    @Column(name = "show_seat_id")
    private Long showSeatId;

    protected BookingItemId() { }
    public BookingItemId(String bookingId, Long showSeatId) { this.bookingId = bookingId; this.showSeatId = showSeatId; }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof BookingItemId that
                && Objects.equals(bookingId, that.bookingId) && Objects.equals(showSeatId, that.showSeatId);
    }
    @Override public int hashCode() { return Objects.hash(bookingId, showSeatId); }
}
