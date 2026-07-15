package com.moviebooking.pricing;

import com.moviebooking.entity.PricingTier;
import com.moviebooking.pricing.PricingDtos.PricingTierRequest;
import com.moviebooking.pricing.PricingDtos.PricingTierResponse;
import com.moviebooking.repository.PricingTierRepository;
import com.moviebooking.web.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PricingTierService {

    private final PricingTierRepository pricingTierRepository;

    public PricingTierService(PricingTierRepository pricingTierRepository) {
        this.pricingTierRepository = pricingTierRepository;
    }

    public List<PricingTierResponse> list() {
        return pricingTierRepository.findAll().stream().map(this::toResponse).toList();
    }

    /** Creates a new tier or updates the multiplier/description of an existing one (upsert by code). */
    @Transactional
    public PricingTierResponse save(PricingTierRequest request) {
        PricingTier tier = pricingTierRepository.findByCode(request.code())
                .orElseGet(PricingTier::new);
        tier.setCode(request.code());
        tier.setDescription(request.description());
        tier.setMultiplier(request.multiplier());
        return toResponse(pricingTierRepository.save(tier));
    }

    @Transactional
    public void delete(Long id) {
        if (!pricingTierRepository.existsById(id)) {
            throw ApiException.notFound("Pricing tier not found: " + id);
        }
        pricingTierRepository.deleteById(id);
    }

    private PricingTierResponse toResponse(PricingTier tier) {
        return new PricingTierResponse(tier.getId(), tier.getCode(), tier.getDescription(), tier.getMultiplier());
    }
}
