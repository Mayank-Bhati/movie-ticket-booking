package com.mayankbhati.movietickets.catalog.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mayankbhati.movietickets.catalog.domain.PricingTier;

public interface PricingTierRepository extends JpaRepository<PricingTier, String> {
}
