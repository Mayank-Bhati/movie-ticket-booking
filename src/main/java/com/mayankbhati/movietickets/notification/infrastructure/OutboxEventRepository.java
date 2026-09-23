package com.mayankbhati.movietickets.notification.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.mayankbhati.movietickets.notification.domain.OutboxEvent;

import jakarta.persistence.LockModeType;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    boolean existsByDedupeKey(String dedupeKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OutboxEvent> findByStatusAndAvailableAtLessThanEqualOrderByIdAsc(
            String status, OffsetDateTime availableAt, Pageable pageable);
}
