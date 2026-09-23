package com.mayankbhati.movietickets.catalog.infrastructure;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.mayankbhati.movietickets.catalog.domain.ShowSeat;

import jakarta.persistence.LockModeType;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {
    @EntityGraph(attributePaths = "seat")
    List<ShowSeat> findByShowingIdOrderBySeatRowLabelAscSeatSeatNumberAsc(long showingId);

    List<ShowSeat> findByShowingIdIn(Collection<Long> showingIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "seat")
    List<ShowSeat> findByShowingIdAndIdInOrderByIdAsc(long showingId, Collection<Long> seatIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "seat")
    List<ShowSeat> findByHoldIdOrderByIdAsc(String holdId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ShowSeat> findByBookingIdOrderByIdAsc(String bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ShowSeat> findByStatusAndHoldExpiresAtLessThanEqualOrderByIdAsc(
            String status, OffsetDateTime expiresAt);
}
