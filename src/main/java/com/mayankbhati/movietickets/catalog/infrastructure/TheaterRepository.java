package com.mayankbhati.movietickets.catalog.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mayankbhati.movietickets.catalog.domain.Theater;

public interface TheaterRepository extends JpaRepository<Theater, Long> {
}
