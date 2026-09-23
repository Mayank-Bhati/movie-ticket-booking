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
@Table(name = "booking_item")
public class BookingItem {
    @EmbeddedId
    private BookingItemId id;
    @MapsId("bookingId") @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;
    @MapsId("showSeatId") @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_seat_id")
    private ShowSeat showSeat;
    @Column(name = "seat_label", nullable = false, length = 30)
    private String seatLabel;
    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    protected BookingItem() { }
    BookingItem(Booking booking, ShowSeat showSeat, String seatLabel, long priceCents) {
        this.id = new BookingItemId(booking.getId(), showSeat.getId());
        this.booking = booking; this.showSeat = showSeat;
        this.seatLabel = seatLabel; this.priceCents = priceCents;
    }
    public String getSeatLabel() { return seatLabel; }
}
