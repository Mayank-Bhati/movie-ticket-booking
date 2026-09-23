package com.mayankbhati.movietickets.catalog.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mayankbhati.movietickets.catalog.domain.City;

public interface CityRepository extends JpaRepository<City, Long> {
    List<City> findAllByOrderByNameAsc();
}
