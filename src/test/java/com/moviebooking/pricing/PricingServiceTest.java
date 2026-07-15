package com.moviebooking.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.moviebooking.entity.PricingTier;
import com.moviebooking.entity.Seat;
import com.moviebooking.entity.Show;
import com.moviebooking.repository.PricingTierRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PricingServiceTest {

    @Mock
    private PricingTierRepository pricingTierRepository;

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingService(pricingTierRepository);
        stubTier(PricingTier.SEAT_REGULAR, "1.00");
        stubTier(PricingTier.SEAT_PREMIUM, "1.50");
        stubTier(PricingTier.WEEKEND, "1.25");
    }

    @Test
    void regularSeatOnWeekdayUsesBasePrice() {
        BigDecimal price = pricingService.priceFor(show("2026-07-22T18:00"), seat(Seat.SeatType.REGULAR));
        assertThat(price).isEqualByComparingTo("200.00");
    }

    @Test
    void premiumSeatAppliesPremiumMultiplier() {
        BigDecimal price = pricingService.priceFor(show("2026-07-22T18:00"), seat(Seat.SeatType.PREMIUM));
        assertThat(price).isEqualByComparingTo("300.00");
    }

    @Test
    void weekendRegularSeatAppliesWeekendMultiplier() {
        BigDecimal price = pricingService.priceFor(show("2026-07-18T18:00"), seat(Seat.SeatType.REGULAR));
        assertThat(price).isEqualByComparingTo("250.00");
    }

    @Test
    void weekendPremiumStacksBothMultipliers() {
        BigDecimal price = pricingService.priceFor(show("2026-07-18T18:00"), seat(Seat.SeatType.PREMIUM));
        assertThat(price).isEqualByComparingTo("375.00");
    }

    private void stubTier(String code, String multiplier) {
        PricingTier tier = new PricingTier();
        tier.setCode(code);
        tier.setMultiplier(new BigDecimal(multiplier));
        when(pricingTierRepository.findByCode(code)).thenReturn(Optional.of(tier));
    }

    private Show show(String startsAt) {
        Show show = new Show();
        show.setBasePrice(new BigDecimal("200"));
        show.setStartsAt(LocalDateTime.parse(startsAt));
        return show;
    }

    private Seat seat(Seat.SeatType type) {
        Seat seat = new Seat();
        seat.setSeatType(type);
        return seat;
    }
}
