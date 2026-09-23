package com.mayankbhati.movietickets.catalog.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mayankbhati.movietickets.catalog.domain.RefundPolicy;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, Long> {
}
