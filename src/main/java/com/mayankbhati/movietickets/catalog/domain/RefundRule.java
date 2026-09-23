package com.mayankbhati.movietickets.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "refund_rule")
public class RefundRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private RefundPolicy policy;
    @Column(name = "minimum_minutes_before", nullable = false)
    private long minimumMinutesBefore;
    @Column(name = "refund_percent", nullable = false)
    private int refundPercent;

    protected RefundRule() { }
    RefundRule(RefundPolicy policy, long minimumMinutesBefore, int refundPercent) {
        this.policy = policy; this.minimumMinutesBefore = minimumMinutesBefore;
        this.refundPercent = refundPercent;
    }
    public long getMinimumMinutesBefore() { return minimumMinutesBefore; }
    public int getRefundPercent() { return refundPercent; }
}
