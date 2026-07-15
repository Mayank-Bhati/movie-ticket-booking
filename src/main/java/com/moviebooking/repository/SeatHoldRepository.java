package com.moviebooking.repository;

import com.moviebooking.entity.SeatHold;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatHoldRepository extends JpaRepository<SeatHold, Long> {

    List<SeatHold> findByStatusAndExpiresAtBefore(SeatHold.Status status, LocalDateTime cutoff);
}
