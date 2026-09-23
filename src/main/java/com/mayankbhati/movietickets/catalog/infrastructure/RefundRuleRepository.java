package com.mayankbhati.movietickets.catalog.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mayankbhati.movietickets.catalog.domain.RefundRule;

public interface RefundRuleRepository extends JpaRepository<RefundRule, Long> {
    Optional<RefundRule> findFirstByPolicyIdAndMinimumMinutesBeforeLessThanEqualOrderByMinimumMinutesBeforeDesc(
            long policyId, long minutesBefore);
}
