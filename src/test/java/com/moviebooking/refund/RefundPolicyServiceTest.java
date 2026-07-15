package com.moviebooking.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.moviebooking.entity.RefundRule;
import com.moviebooking.repository.RefundRuleRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundPolicyServiceTest {

    @Mock
    private RefundRuleRepository refundRuleRepository;

    private RefundPolicyService refundPolicyService;

    @BeforeEach
    void setUp() {
        refundPolicyService = new RefundPolicyService(refundRuleRepository);
        when(refundRuleRepository.findAllByOrderByMinHoursBeforeShowDesc())
                .thenReturn(List.of(rule(24, 100), rule(2, 50), rule(0, 0)));
    }

    @Test
    void fullRefundWhenCancellingWellBeforeShow() {
        LocalDateTime now = LocalDateTime.parse("2026-07-15T10:00");
        LocalDateTime show = now.plusHours(48);
        assertThat(refundPolicyService.refundPercentFor(now, show)).isEqualTo(100);
    }

    @Test
    void halfRefundWithinTheDayBeforeShow() {
        LocalDateTime now = LocalDateTime.parse("2026-07-15T10:00");
        LocalDateTime show = now.plusHours(3);
        assertThat(refundPolicyService.refundPercentFor(now, show)).isEqualTo(50);
    }

    @Test
    void noRefundCloseToShow() {
        LocalDateTime now = LocalDateTime.parse("2026-07-15T10:00");
        LocalDateTime show = now.plusMinutes(30);
        assertThat(refundPolicyService.refundPercentFor(now, show)).isEqualTo(0);
    }

    @Test
    void noRefundAfterShowHasStarted() {
        LocalDateTime now = LocalDateTime.parse("2026-07-15T10:00");
        LocalDateTime show = now.minusHours(1);
        assertThat(refundPolicyService.refundPercentFor(now, show)).isEqualTo(0);
    }

    private RefundRule rule(int minHours, int percent) {
        RefundRule rule = new RefundRule();
        rule.setMinHoursBeforeShow(minHours);
        rule.setRefundPercent(percent);
        return rule;
    }
}
