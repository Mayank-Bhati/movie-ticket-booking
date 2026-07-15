package com.moviebooking.pricing;

import com.moviebooking.entity.DiscountCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Request/response payloads for pricing tiers and discount codes. */
public class PricingDtos {

    // ----- Pricing tier -----
    public record PricingTierRequest(
            @NotBlank String code,
            String description,
            @NotNull @DecimalMin("0.0") BigDecimal multiplier) {
    }

    public record PricingTierResponse(Long id, String code, String description, BigDecimal multiplier) {
    }

    // ----- Discount code -----
    public record DiscountCodeRequest(
            @NotBlank String code,
            @NotNull DiscountCode.Type type,
            @NotNull @Positive BigDecimal amount,
            @DecimalMin("0.0") BigDecimal minAmount,
            @Min(1) Integer maxUses,
            LocalDateTime validFrom,
            LocalDateTime validUntil,
            Boolean active) {
    }

    public record DiscountCodeResponse(Long id, String code, String type, BigDecimal amount,
                                       BigDecimal minAmount, Integer maxUses, Integer usedCount,
                                       LocalDateTime validFrom, LocalDateTime validUntil, boolean active) {
    }

    // ----- Discount preview -----
    public record DiscountPreviewResponse(String code, BigDecimal subtotal,
                                          BigDecimal discountAmount, BigDecimal total) {
    }
}
