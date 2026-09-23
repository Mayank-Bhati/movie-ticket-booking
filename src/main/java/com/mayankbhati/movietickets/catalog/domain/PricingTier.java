package com.mayankbhati.movietickets.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pricing_tier")
public class PricingTier {
    @Id @Column(length = 20)
    private String category;
    @Column(name = "multiplier_bps", nullable = false)
    private int multiplierBps;

    protected PricingTier() { }
    public String getCategory() { return category; }
    public int getMultiplierBps() { return multiplierBps; }
    public void changeMultiplier(int multiplierBps) { this.multiplierBps = multiplierBps; }
}
