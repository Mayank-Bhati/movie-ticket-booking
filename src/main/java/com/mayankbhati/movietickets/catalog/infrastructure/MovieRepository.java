package com.mayankbhati.movietickets.catalog.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mayankbhati.movietickets.catalog.domain.Movie;

public interface MovieRepository extends JpaRepository<Movie, Long> {
    List<Movie> findAllByOrderByTitleAsc();
}
