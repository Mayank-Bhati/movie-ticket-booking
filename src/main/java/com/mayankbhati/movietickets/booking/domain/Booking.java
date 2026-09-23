package com.mayankbhati.movietickets.booking.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.mayankbhati.movietickets.catalog.domain.ShowSeat;
import com.mayankbhati.movietickets.catalog.domain.Showing;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

@Entity
public class Booking {
    @Id @Column(length = 36)
    private String id;
    @Column(name = "customer_id", nullable = false)
    private long customerId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "showing_id", nullable = false)
    private Showing showing;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hold_id", nullable = false, unique = true)
    private SeatHold hold;
    @Column(name = "idempotency_key", nullable = false, length = 80)
    private String idempotencyKey;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "subtotal_cents", nullable = false)
    private long subtotalCents;
    @Column(name = "discount_cents", nullable = false)
    private long discountCents;
    @Column(name = "total_cents", nullable = false)
    private long totalCents;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "discount_code", length = 40)
    private String discountCode;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingItem> items = new ArrayList<>();

    protected Booking() { }
    public Booking(String id, long customerId, Showing showing, SeatHold hold, String idempotencyKey,
                   long subtotalCents, long discountCents, String discountCode, OffsetDateTime createdAt) {
        this.id = id; this.customerId = customerId; this.showing = showing; this.hold = hold;
        this.idempotencyKey = idempotencyKey; this.status = "CONFIRMED";
        this.subtotalCents = subtotalCents; this.discountCents = discountCents;
        this.totalCents = subtotalCents - discountCents; this.currency = "INR";
        this.discountCode = discountCode; this.createdAt = createdAt;
    }
    public void addItem(ShowSeat seat) { items.add(new BookingItem(this, seat, seat.getSeat().label(), seat.getPriceCents())); }
    public void cancel(OffsetDateTime at) { status = "CANCELLED"; cancelledAt = at; }
    public String getId() { return id; }
    public long getCustomerId() { return customerId; }
    public Showing getShowing() { return showing; }
    public String getStatus() { return status; }
    public long getSubtotalCents() { return subtotalCents; }
    public long getDiscountCents() { return discountCents; }
    public long getTotalCents() { return totalCents; }
    public String getCurrency() { return currency; }
    public String getDiscountCode() { return discountCode; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public List<BookingItem> getItems() { return List.copyOf(items); }
}
