package com.moviebooking.refund;

import com.moviebooking.entity.RefundRule;
import com.moviebooking.refund.RefundDtos.RefundRuleRequest;
import com.moviebooking.refund.RefundDtos.RefundRuleResponse;
import com.moviebooking.repository.RefundRuleRepository;
import com.moviebooking.web.ApiException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the configurable refund policy: a set of tiers keyed by "minimum hours before the show".
 * The applicable tier is the one with the largest threshold that the cancellation still meets,
 * e.g. tiers {24h→100%, 2h→50%, 0h→0%} refund fully at 25h out, half at 3h, nothing at 1h.
 */
@Service
public class RefundPolicyService {

    private final RefundRuleRepository refundRuleRepository;

    public RefundPolicyService(RefundRuleRepository refundRuleRepository) {
        this.refundRuleRepository = refundRuleRepository;
    }

    /** Resolves the refund percentage for a cancellation happening {@code now} before {@code showStart}. */
    public int refundPercentFor(LocalDateTime now, LocalDateTime showStart) {
        long hoursUntilShow = Duration.between(now, showStart).toHours();
        return refundRuleRepository.findAllByOrderByMinHoursBeforeShowDesc().stream()
                .filter(rule -> hoursUntilShow >= rule.getMinHoursBeforeShow())
                .map(RefundRule::getRefundPercent)
                .findFirst()
                .orElse(0);
    }

    public List<RefundRuleResponse> list() {
        return refundRuleRepository.findAllByOrderByMinHoursBeforeShowDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    /** Creates a refund tier or updates the percentage of an existing one (upsert by threshold). */
    @Transactional
    public RefundRuleResponse save(RefundRuleRequest request) {
        RefundRule rule = refundRuleRepository.findByMinHoursBeforeShow(request.minHoursBeforeShow())
                .orElseGet(RefundRule::new);
        rule.setMinHoursBeforeShow(request.minHoursBeforeShow());
        rule.setRefundPercent(request.refundPercent());
        return toResponse(refundRuleRepository.save(rule));
    }

    @Transactional
    public void delete(Long id) {
        if (!refundRuleRepository.existsById(id)) {
            throw ApiException.notFound("Refund rule not found: " + id);
        }
        refundRuleRepository.deleteById(id);
    }

    private RefundRuleResponse toResponse(RefundRule rule) {
        return new RefundRuleResponse(rule.getId(), rule.getMinHoursBeforeShow(), rule.getRefundPercent());
    }
}
