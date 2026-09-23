package com.mayankbhati.movietickets.booking.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;

@Entity
public class Refund {
    @Id @Column(length = 36)
    private String id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;
    @Column(name = "amount_cents", nullable = false)
    private long amountCents;
    @Column(name = "refund_percent", nullable = false)
    private int refundPercent;
    @Column(name = "provider_reference", length = 100)
    private String providerReference;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Refund() { }
    public Refund(String id, Booking booking, long amountCents, int refundPercent,
                  String providerReference, OffsetDateTime createdAt) {
        this.id = id; this.booking = booking; this.amountCents = amountCents;
        this.refundPercent = refundPercent; this.providerReference = providerReference; this.createdAt = createdAt;
    }
}
