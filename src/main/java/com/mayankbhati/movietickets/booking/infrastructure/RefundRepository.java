package com.mayankbhati.movietickets.booking.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mayankbhati.movietickets.booking.domain.Refund;

public interface RefundRepository extends JpaRepository<Refund, String> {
}
