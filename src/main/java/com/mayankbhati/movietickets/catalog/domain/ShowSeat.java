package com.mayankbhati.movietickets.catalog.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "show_seat")
public class ShowSeat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "showing_id", nullable = false)
    private Showing showing;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;
    @Column(nullable = false, length = 20)
    private String category;
    @Column(name = "price_cents", nullable = false)
    private long priceCents;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "hold_id", length = 36)
    private String holdId;
    @Column(name = "hold_expires_at")
    private OffsetDateTime holdExpiresAt;
    @Column(name = "booking_id", length = 36)
    private String bookingId;
    @Version
    private long version;

    protected ShowSeat() { }
    public ShowSeat(Showing showing, Seat seat, long priceCents) {
        this.showing = showing; this.seat = seat; this.category = seat.getCategory();
        this.priceCents = priceCents; this.status = "AVAILABLE";
    }
    public Long getId() { return id; }
    public Showing getShowing() { return showing; }
    public Seat getSeat() { return seat; }
    public String getCategory() { return category; }
    public long getPriceCents() { return priceCents; }
    public String getStatus() { return status; }
    public String getHoldId() { return holdId; }
    public String visibleStatus(OffsetDateTime now) {
        return "HELD".equals(status) && !holdExpiresAt.isAfter(now) ? "AVAILABLE" : status;
    }
    public void hold(String holdId, OffsetDateTime expiresAt) {
        this.status = "HELD"; this.holdId = holdId; this.holdExpiresAt = expiresAt;
    }
    public void book(String bookingId) {
        this.status = "BOOKED"; this.bookingId = bookingId;
        this.holdId = null; this.holdExpiresAt = null;
    }
    public void release() {
        this.status = "AVAILABLE"; this.holdId = null; this.holdExpiresAt = null; this.bookingId = null;
    }
}
