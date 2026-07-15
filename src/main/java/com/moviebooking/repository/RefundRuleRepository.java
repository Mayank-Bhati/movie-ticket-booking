package com.moviebooking.repository;

import com.moviebooking.entity.RefundRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRuleRepository extends JpaRepository<RefundRule, Long> {

    List<RefundRule> findAllByOrderByMinHoursBeforeShowDesc();

    Optional<RefundRule> findByMinHoursBeforeShow(Integer minHoursBeforeShow);
}
