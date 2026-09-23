package com.mayankbhati.movietickets.catalog.infrastructure;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mayankbhati.movietickets.catalog.domain.Screen;

public interface ScreenRepository extends JpaRepository<Screen, Long> {
    @Override
    @EntityGraph(attributePaths = {"theater", "theater.city"})
    java.util.Optional<Screen> findById(Long id);
}
