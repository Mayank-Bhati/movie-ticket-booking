package com.mayankbhati.movietickets.catalog.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

@Entity
public class RefundPolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 120)
    private String name;
    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RefundRule> rules = new ArrayList<>();

    protected RefundPolicy() { }
    public RefundPolicy(String name) { this.name = name; }
    public void addRule(long minimumMinutesBefore, int refundPercent) {
        rules.add(new RefundRule(this, minimumMinutesBefore, refundPercent));
    }
    public Long getId() { return id; }
    public List<RefundRule> getRules() { return List.copyOf(rules); }
}
