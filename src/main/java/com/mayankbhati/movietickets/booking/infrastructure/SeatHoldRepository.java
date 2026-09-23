package com.mayankbhati.movietickets.booking.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.mayankbhati.movietickets.booking.domain.SeatHold;

import jakarta.persistence.LockModeType;

public interface SeatHoldRepository extends JpaRepository<SeatHold, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "showing")
    Optional<SeatHold> findLockedById(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<SeatHold> findByStatusAndExpiresAtLessThanEqual(String status, OffsetDateTime expiresAt);
}
