package com.mayankbhati.movietickets.catalog.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mayankbhati.movietickets.catalog.domain.Seat;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByScreenIdOrderByIdAsc(long screenId);
}
