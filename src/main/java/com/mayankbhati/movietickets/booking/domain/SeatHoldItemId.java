package com.mayankbhati.movietickets.booking.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class SeatHoldItemId implements Serializable {
    @Column(name = "hold_id", length = 36)
    private String holdId;
    @Column(name = "show_seat_id")
    private Long showSeatId;

    protected SeatHoldItemId() { }
    public SeatHoldItemId(String holdId, Long showSeatId) { this.holdId = holdId; this.showSeatId = showSeatId; }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof SeatHoldItemId that
                && Objects.equals(holdId, that.holdId) && Objects.equals(showSeatId, that.showSeatId);
    }
    @Override public int hashCode() { return Objects.hash(holdId, showSeatId); }
}
