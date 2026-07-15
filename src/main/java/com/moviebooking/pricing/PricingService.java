package com.moviebooking.pricing;

import com.moviebooking.entity.PricingTier;
import com.moviebooking.entity.Seat;
import com.moviebooking.entity.Show;
import com.moviebooking.repository.PricingTierRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import org.springframework.stereotype.Service;

/**
 * Computes the price of a seat for a show:
 * base price × seat-type multiplier × (weekend multiplier if the show falls on Sat/Sun).
 * Multipliers come from the pricing_tiers table and are admin-configurable.
 */
@Service
public class PricingService {

    private final PricingTierRepository pricingTierRepository;

    public PricingService(PricingTierRepository pricingTierRepository) {
        this.pricingTierRepository = pricingTierRepository;
    }

    public BigDecimal priceFor(Show show, Seat seat) {
        BigDecimal price = show.getBasePrice().multiply(seatTypeMultiplier(seat.getSeatType()));
        if (isWeekend(show)) {
            price = price.multiply(multiplier(PricingTier.WEEKEND));
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal seatTypeMultiplier(Seat.SeatType type) {
        String code = type == Seat.SeatType.PREMIUM ? PricingTier.SEAT_PREMIUM : PricingTier.SEAT_REGULAR;
        return multiplier(code);
    }

    private BigDecimal multiplier(String code) {
        return pricingTierRepository.findByCode(code)
                .map(PricingTier::getMultiplier)
                .orElse(BigDecimal.ONE);
    }

    private boolean isWeekend(Show show) {
        DayOfWeek day = show.getStartsAt().getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
