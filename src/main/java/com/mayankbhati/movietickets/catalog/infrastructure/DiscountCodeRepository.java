package com.mayankbhati.movietickets.catalog.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.mayankbhati.movietickets.catalog.domain.DiscountCode;

import jakarta.persistence.LockModeType;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DiscountCode> findLockedByCode(String code);
}
