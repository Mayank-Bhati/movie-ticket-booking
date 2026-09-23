package com.mayankbhati.movietickets.booking.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;

@Entity
public class Payment {
    @Id @Column(length = 36)
    private String id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;
    @Column(name = "amount_cents", nullable = false)
    private long amountCents;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "provider_reference", nullable = false, unique = true, length = 100)
    private String providerReference;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Payment() { }
    public Payment(String id, Booking booking, long amountCents, String providerReference, OffsetDateTime createdAt) {
        this.id = id; this.booking = booking; this.amountCents = amountCents;
        this.providerReference = providerReference; this.createdAt = createdAt; this.status = "CAPTURED";
    }
    public String getProviderReference() { return providerReference; }
    public void markRefunded() { status = "REFUNDED"; }
}
