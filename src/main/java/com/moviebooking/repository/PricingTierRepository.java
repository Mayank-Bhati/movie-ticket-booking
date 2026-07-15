package com.moviebooking.repository;

import com.moviebooking.entity.PricingTier;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingTierRepository extends JpaRepository<PricingTier, Long> {

    Optional<PricingTier> findByCode(String code);
}
