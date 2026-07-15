package com.moviebooking.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.moviebooking.entity.DiscountCode;
import com.moviebooking.pricing.DiscountService.DiscountResult;
import com.moviebooking.repository.DiscountCodeRepository;
import com.moviebooking.web.ApiException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

    @Mock
    private DiscountCodeRepository discountCodeRepository;

    private DiscountService discountService() {
        return new DiscountService(discountCodeRepository);
    }

    @Test
    void percentDiscountComputesPercentageOfSubtotal() {
        stub(percent("SAVE10", "10"));
        DiscountResult result = discountService().apply("SAVE10", new BigDecimal("1000"));
        assertThat(result.discountAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void flatDiscountSubtractsFixedAmount() {
        stub(flat("FLAT50", "50"));
        DiscountResult result = discountService().apply("FLAT50", new BigDecimal("1000"));
        assertThat(result.discountAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void discountNeverExceedsSubtotal() {
        stub(flat("FLAT50", "50"));
        DiscountResult result = discountService().apply("FLAT50", new BigDecimal("30"));
        assertThat(result.discountAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void unknownCodeIsRejected() {
        when(discountCodeRepository.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> discountService().apply("NOPE", new BigDecimal("100")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unknown discount code");
    }

    @Test
    void inactiveCodeIsRejected() {
        DiscountCode code = percent("SAVE10", "10");
        code.setActive(false);
        stub(code);
        assertThatThrownBy(() -> discountService().apply("SAVE10", new BigDecimal("100")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void expiredCodeIsRejected() {
        DiscountCode code = percent("SAVE10", "10");
        code.setValidUntil(LocalDateTime.now().minusDays(1));
        stub(code);
        assertThatThrownBy(() -> discountService().apply("SAVE10", new BigDecimal("100")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void belowMinimumAmountIsRejected() {
        DiscountCode code = percent("SAVE10", "10");
        code.setMinAmount(new BigDecimal("500"));
        stub(code);
        assertThatThrownBy(() -> discountService().apply("SAVE10", new BigDecimal("100")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("minimum amount");
    }

    @Test
    void usageLimitReachedIsRejected() {
        DiscountCode code = percent("SAVE10", "10");
        code.setMaxUses(2);
        code.setUsedCount(2);
        stub(code);
        assertThatThrownBy(() -> discountService().apply("SAVE10", new BigDecimal("100")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("usage limit");
    }

    private void stub(DiscountCode code) {
        when(discountCodeRepository.findByCodeIgnoreCase(code.getCode())).thenReturn(Optional.of(code));
    }

    private DiscountCode percent(String code, String amount) {
        return build(code, DiscountCode.Type.PERCENT, amount);
    }

    private DiscountCode flat(String code, String amount) {
        return build(code, DiscountCode.Type.FLAT, amount);
    }

    private DiscountCode build(String codeStr, DiscountCode.Type type, String amount) {
        DiscountCode code = new DiscountCode();
        code.setCode(codeStr);
        code.setType(type);
        code.setAmount(new BigDecimal(amount));
        code.setActive(true);
        code.setUsedCount(0);
        return code;
    }
}
