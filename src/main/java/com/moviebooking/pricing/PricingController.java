package com.moviebooking.pricing;

import com.moviebooking.pricing.PricingDtos.DiscountCodeRequest;
import com.moviebooking.pricing.PricingDtos.DiscountCodeResponse;
import com.moviebooking.pricing.PricingDtos.DiscountPreviewResponse;
import com.moviebooking.pricing.PricingDtos.PricingTierRequest;
import com.moviebooking.pricing.PricingDtos.PricingTierResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PricingController {

    private final PricingTierService pricingTierService;
    private final DiscountService discountService;

    public PricingController(PricingTierService pricingTierService, DiscountService discountService) {
        this.pricingTierService = pricingTierService;
        this.discountService = discountService;
    }

    // ----- Pricing tiers (admin) -----
    @GetMapping("/api/admin/pricing-tiers")
    public List<PricingTierResponse> listTiers() {
        return pricingTierService.list();
    }

    @PutMapping("/api/admin/pricing-tiers")
    public PricingTierResponse saveTier(@Valid @RequestBody PricingTierRequest request) {
        return pricingTierService.save(request);
    }

    @DeleteMapping("/api/admin/pricing-tiers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTier(@PathVariable Long id) {
        pricingTierService.delete(id);
    }

    // ----- Discount codes (admin) -----
    @GetMapping("/api/admin/discount-codes")
    public List<DiscountCodeResponse> listCodes() {
        return discountService.list();
    }

    @PostMapping("/api/admin/discount-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public DiscountCodeResponse createCode(@Valid @RequestBody DiscountCodeRequest request) {
        return discountService.create(request);
    }

    @PutMapping("/api/admin/discount-codes/{id}")
    public DiscountCodeResponse updateCode(@PathVariable Long id, @Valid @RequestBody DiscountCodeRequest request) {
        return discountService.update(id, request);
    }

    @DeleteMapping("/api/admin/discount-codes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCode(@PathVariable Long id) {
        discountService.delete(id);
    }

    // ----- Discount preview (any authenticated user) -----
    public record PreviewRequest(@NotBlank String code, @NotNull @Positive BigDecimal subtotal) {
    }

    @PostMapping("/api/discounts/preview")
    public DiscountPreviewResponse preview(@Valid @RequestBody PreviewRequest request) {
        return discountService.preview(request.code(), request.subtotal());
    }
}
