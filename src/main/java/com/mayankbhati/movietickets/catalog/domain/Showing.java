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

@Entity
public class Showing {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_policy_id", nullable = false)
    private RefundPolicy refundPolicy;
    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;
    @Column(name = "base_price_cents", nullable = false)
    private long basePriceCents;
    @Column(name = "weekend_multiplier_bps", nullable = false)
    private int weekendMultiplierBps;
    @Column(nullable = false, length = 20)
    private String status;

    protected Showing() { }
    public Showing(Movie movie, Screen screen, RefundPolicy refundPolicy, OffsetDateTime startsAt,
                   long basePriceCents, int weekendMultiplierBps) {
        this.movie = movie; this.screen = screen; this.refundPolicy = refundPolicy;
        this.startsAt = startsAt; this.basePriceCents = basePriceCents;
        this.weekendMultiplierBps = weekendMultiplierBps; this.status = "SCHEDULED";
    }
    public Long getId() { return id; }
    public Movie getMovie() { return movie; }
    public Screen getScreen() { return screen; }
    public RefundPolicy getRefundPolicy() { return refundPolicy; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public String getStatus() { return status; }
}
