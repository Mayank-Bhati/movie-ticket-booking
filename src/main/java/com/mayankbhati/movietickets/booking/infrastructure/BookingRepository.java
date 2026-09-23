package com.mayankbhati.movietickets.booking.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.mayankbhati.movietickets.booking.domain.Booking;

import jakarta.persistence.LockModeType;

public interface BookingRepository extends JpaRepository<Booking, String> {
    Optional<Booking> findByCustomerIdAndIdempotencyKey(long customerId, String idempotencyKey);

    @EntityGraph(attributePaths = {"showing", "showing.movie", "items"})
    Optional<Booking> findDetailedByIdAndCustomerId(String id, long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"showing", "showing.refundPolicy"})
    Optional<Booking> findLockedById(String id);

    @EntityGraph(attributePaths = {"showing", "showing.movie"})
    List<Booking> findByCustomerIdOrderByCreatedAtDesc(long customerId);

    @EntityGraph(attributePaths = {"showing", "showing.movie"})
    List<Booking> findByStatusAndShowingStartsAtGreaterThanEqualAndShowingStartsAtLessThan(
            String status, OffsetDateTime from, OffsetDateTime to);
}
