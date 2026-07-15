package com.moviebooking.repository;

import com.moviebooking.entity.Booking;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
            SELECT b FROM Booking b
            WHERE b.status = com.moviebooking.entity.Booking.Status.CONFIRMED
              AND b.show.startsAt BETWEEN :from AND :to
            """)
    List<Booking> findConfirmedWithShowStartingBetween(@Param("from") LocalDateTime from,
                                                       @Param("to") LocalDateTime to);
}
