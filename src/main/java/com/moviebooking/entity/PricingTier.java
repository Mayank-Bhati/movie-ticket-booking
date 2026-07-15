package com.moviebooking.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pricing_tiers")
public class PricingTier {

    public static final String SEAT_REGULAR = "SEAT_REGULAR";
    public static final String SEAT_PREMIUM = "SEAT_PREMIUM";
    public static final String WEEKEND = "WEEKEND";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    private String description;

    @Column(nullable = false)
    private BigDecimal multiplier;
}
