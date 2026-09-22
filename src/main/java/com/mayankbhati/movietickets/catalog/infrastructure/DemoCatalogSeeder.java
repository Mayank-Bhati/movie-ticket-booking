package com.mayankbhati.movietickets.catalog.infrastructure;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.mayankbhati.movietickets.catalog.application.CatalogService;
import com.mayankbhati.movietickets.catalog.application.CatalogService.RefundRuleInput;

@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
class DemoCatalogSeeder implements ApplicationRunner {
    private final CatalogService catalog;
    private final Clock clock;

    DemoCatalogSeeder(CatalogService catalog, Clock clock) {
        this.catalog = catalog;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!catalog.cities().isEmpty()) {
            return;
        }
        long cityId = catalog.createCity("Bengaluru", "Asia/Kolkata");
        long theaterId = catalog.createTheater(cityId, "Orion Cinema", "Dr Rajkumar Road");
        CatalogService.ScreenCreated screen = catalog.createScreen(theaterId, "Audi 1", 6, 8,
                Set.of(5, 6));
        long movieId = catalog.createMovie("The Last Algorithm", 132, "U/A", "English");
        long refundPolicyId = catalog.createRefundPolicy("Standard", List.of(
                new RefundRuleInput(0, 0),
                new RefundRuleInput(120, 50),
                new RefundRuleInput(1440, 100)));

        ZonedDateTime start = clock.instant().atZone(java.time.ZoneId.of("Asia/Kolkata"))
                .plusDays(2).withHour(19).withMinute(30).withSecond(0).withNano(0);
        catalog.createShowing(movieId, screen.id(), refundPolicyId, start.toInstant(), 30000, 12500);
        catalog.createDiscount("WELCOME10", "PERCENT", 10, 0,
                clock.instant().minusSeconds(60), clock.instant().plusSeconds(30L * 24 * 60 * 60), 100);
    }
}
