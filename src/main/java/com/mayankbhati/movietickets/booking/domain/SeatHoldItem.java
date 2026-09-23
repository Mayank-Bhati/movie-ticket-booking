package com.mayankbhati.movietickets.booking.domain;

import com.mayankbhati.movietickets.catalog.domain.ShowSeat;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "seat_hold_item")
public class SeatHoldItem {
    @EmbeddedId
    private SeatHoldItemId id;
    @MapsId("holdId") @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hold_id")
    private SeatHold hold;
    @MapsId("showSeatId") @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_seat_id")
    private ShowSeat showSeat;
    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    protected SeatHoldItem() { }
    SeatHoldItem(SeatHold hold, ShowSeat showSeat, long priceCents) {
        this.id = new SeatHoldItemId(hold.getId(), showSeat.getId());
        this.hold = hold; this.showSeat = showSeat; this.priceCents = priceCents;
    }
}
