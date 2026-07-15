package com.moviebooking.repository;

import com.moviebooking.entity.ShowSeat;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    List<ShowSeat> findByShowIdOrderByIdAsc(Long showId);

    /**
     * Locks the requested show seats FOR UPDATE, ordered by id to avoid deadlocks.
     * This is the serialization point that prevents double-allocation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ss FROM ShowSeat ss WHERE ss.show.id = :showId AND ss.id IN :seatIds ORDER BY ss.id ASC")
    List<ShowSeat> lockByShowAndIds(@Param("showId") Long showId, @Param("seatIds") List<Long> seatIds);

    /** Locks the seats currently attached to a hold, ordered by id, for the booking transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ss FROM ShowSeat ss WHERE ss.hold.id = :holdId ORDER BY ss.id ASC")
    List<ShowSeat> lockByHoldId(@Param("holdId") Long holdId);

    List<ShowSeat> findByHoldId(Long holdId);
}
