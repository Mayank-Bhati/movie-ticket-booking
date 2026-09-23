package com.mayankbhati.movietickets.catalog.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "discount_code")
public class DiscountCode {
    @Id @Column(length = 40)
    private String code;
    @Column(nullable = false, length = 20)
    private String kind;
    @Column(name = "value_amount", nullable = false)
    private long valueAmount;
    @Column(name = "minimum_order_cents", nullable = false)
    private long minimumOrderCents;
    @Column(name = "valid_from", nullable = false)
    private OffsetDateTime validFrom;
    @Column(name = "valid_until", nullable = false)
    private OffsetDateTime validUntil;
    @Column(name = "max_redemptions")
    private Integer maxRedemptions;
    @Column(name = "redemption_count", nullable = false)
    private int redemptionCount;
    @Column(nullable = false)
    private boolean active;

    protected DiscountCode() { }
    public DiscountCode(String code, String kind, long valueAmount, long minimumOrderCents,
                        OffsetDateTime validFrom, OffsetDateTime validUntil, Integer maxRedemptions) {
        this.code = code; this.kind = kind; this.valueAmount = valueAmount;
        this.minimumOrderCents = minimumOrderCents; this.validFrom = validFrom;
        this.validUntil = validUntil; this.maxRedemptions = maxRedemptions;
        this.active = true;
    }
    public String getCode() { return code; }
    public boolean isApplicable(long subtotal, OffsetDateTime now) {
        return active && !now.isBefore(validFrom) && now.isBefore(validUntil)
                && subtotal >= minimumOrderCents
                && (maxRedemptions == null || redemptionCount < maxRedemptions);
    }
    public long discountFor(long subtotal) {
        long amount = "PERCENT".equals(kind) ? Math.multiplyExact(subtotal, valueAmount) / 100 : valueAmount;
        return Math.min(subtotal, amount);
    }
    public void redeem() { redemptionCount++; }
}
