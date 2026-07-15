package com.moviebooking.repository;

import com.moviebooking.entity.DiscountCode;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {

    Optional<DiscountCode> findByCodeIgnoreCase(String code);

    /**
     * Locks the code row FOR UPDATE so the usage count can be incremented safely when concurrent
     * bookings redeem the same limited code (prevents exceeding maxUses via a lost update).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DiscountCode d WHERE upper(d.code) = upper(:code)")
    Optional<DiscountCode> lockByCode(@Param("code") String code);

    boolean existsByCodeIgnoreCase(String code);
}
