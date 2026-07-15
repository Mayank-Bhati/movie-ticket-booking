package com.moviebooking.repository;

import com.moviebooking.entity.Screen;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenRepository extends JpaRepository<Screen, Long> {

    List<Screen> findByTheaterId(Long theaterId);
}
