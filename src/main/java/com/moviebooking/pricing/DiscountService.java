package com.moviebooking.pricing;

import com.moviebooking.entity.DiscountCode;
import com.moviebooking.pricing.PricingDtos.DiscountCodeRequest;
import com.moviebooking.pricing.PricingDtos.DiscountCodeResponse;
import com.moviebooking.pricing.PricingDtos.DiscountPreviewResponse;
import com.moviebooking.repository.DiscountCodeRepository;
import com.moviebooking.web.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscountService {

    private final DiscountCodeRepository discountCodeRepository;

    public DiscountService(DiscountCodeRepository discountCodeRepository) {
        this.discountCodeRepository = discountCodeRepository;
    }

    /** Result of applying a code: the entity (for the caller to record usage) and the discount amount. */
    public record DiscountResult(DiscountCode code, BigDecimal discountAmount) {
    }

    /**
     * Validates a discount code against the subtotal and computes the discount amount.
     * Does not persist usage — the caller increments usedCount within its own transaction.
     */
    public DiscountResult apply(String code, BigDecimal subtotal) {
        DiscountCode discount = discountCodeRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> ApiException.badRequest("Unknown discount code: " + code));

        if (!discount.getActive()) {
            throw ApiException.badRequest("Discount code is inactive");
        }
        LocalDateTime now = LocalDateTime.now();
        if (discount.getValidFrom() != null && now.isBefore(discount.getValidFrom())) {
            throw ApiException.badRequest("Discount code is not yet valid");
        }
        if (discount.getValidUntil() != null && now.isAfter(discount.getValidUntil())) {
            throw ApiException.badRequest("Discount code has expired");
        }
        if (discount.getMaxUses() != null && discount.getUsedCount() >= discount.getMaxUses()) {
            throw ApiException.badRequest("Discount code usage limit reached");
        }
        if (discount.getMinAmount() != null && subtotal.compareTo(discount.getMinAmount()) < 0) {
            throw ApiException.badRequest("Order does not meet the minimum amount for this code");
        }

        BigDecimal discountAmount = computeAmount(discount, subtotal);
        return new DiscountResult(discount, discountAmount);
    }

    private BigDecimal computeAmount(DiscountCode discount, BigDecimal subtotal) {
        BigDecimal amount = discount.getType() == DiscountCode.Type.PERCENT
                ? subtotal.multiply(discount.getAmount()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : discount.getAmount();
        // Never discount more than the order total.
        return amount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    public DiscountPreviewResponse preview(String code, BigDecimal subtotal) {
        BigDecimal discountAmount = apply(code, subtotal).discountAmount();
        return new DiscountPreviewResponse(code, subtotal, discountAmount, subtotal.subtract(discountAmount));
    }

    // ----- Admin CRUD -----
    @Transactional
    public DiscountCodeResponse create(DiscountCodeRequest request) {
        if (discountCodeRepository.existsByCodeIgnoreCase(request.code())) {
            throw ApiException.conflict("Discount code already exists: " + request.code());
        }
        DiscountCode discount = new DiscountCode();
        discount.setCode(request.code());
        applyRequest(discount, request);
        discount.setUsedCount(0);
        return toResponse(discountCodeRepository.save(discount));
    }

    @Transactional
    public DiscountCodeResponse update(Long id, DiscountCodeRequest request) {
        DiscountCode discount = discountCodeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Discount code not found: " + id));
        applyRequest(discount, request);
        return toResponse(discountCodeRepository.save(discount));
    }

    public List<DiscountCodeResponse> list() {
        return discountCodeRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(Long id) {
        if (!discountCodeRepository.existsById(id)) {
            throw ApiException.notFound("Discount code not found: " + id);
        }
        discountCodeRepository.deleteById(id);
    }

    private void applyRequest(DiscountCode discount, DiscountCodeRequest request) {
        discount.setType(request.type());
        discount.setAmount(request.amount());
        discount.setMinAmount(request.minAmount());
        discount.setMaxUses(request.maxUses());
        discount.setValidFrom(request.validFrom());
        discount.setValidUntil(request.validUntil());
        discount.setActive(request.active() == null ? Boolean.TRUE : request.active());
    }

    private DiscountCodeResponse toResponse(DiscountCode d) {
        return new DiscountCodeResponse(d.getId(), d.getCode(), d.getType().name(), d.getAmount(),
                d.getMinAmount(), d.getMaxUses(), d.getUsedCount(), d.getValidFrom(), d.getValidUntil(),
                d.getActive());
    }
}
