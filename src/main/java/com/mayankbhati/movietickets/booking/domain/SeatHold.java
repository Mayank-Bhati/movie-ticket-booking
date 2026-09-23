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
import jakarta.persistence.Table;

@Entity
@Table(name = "seat_hold")
public class SeatHold {
    @Id @Column(length = 36)
    private String id;
    @Column(name = "customer_id", nullable = false)
    private long customerId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "showing_id", nullable = false)
    private Showing showing;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @OneToMany(mappedBy = "hold", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SeatHoldItem> items = new ArrayList<>();

    protected SeatHold() { }
    public SeatHold(String id, long customerId, Showing showing, OffsetDateTime expiresAt, OffsetDateTime createdAt) {
        this.id = id; this.customerId = customerId; this.showing = showing;
        this.expiresAt = expiresAt; this.createdAt = createdAt; this.status = "ACTIVE";
    }
    public void addItem(ShowSeat seat) { items.add(new SeatHoldItem(this, seat, seat.getPriceCents())); }
    public String getId() { return id; }
    public long getCustomerId() { return customerId; }
    public Showing getShowing() { return showing; }
    public String getStatus() { return status; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void expire() { status = "EXPIRED"; }
    public void convert() { status = "CONVERTED"; }
}
