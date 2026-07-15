package com.moviebooking.config;

import com.moviebooking.catalog.CatalogDtos.CityRequest;
import com.moviebooking.catalog.CatalogDtos.MovieRequest;
import com.moviebooking.catalog.CatalogDtos.ScreenRequest;
import com.moviebooking.catalog.CatalogDtos.SeatRow;
import com.moviebooking.catalog.CatalogDtos.ShowRequest;
import com.moviebooking.catalog.CatalogDtos.TheaterRequest;
import com.moviebooking.catalog.CatalogService;
import com.moviebooking.catalog.ShowService;
import com.moviebooking.entity.Seat;
import com.moviebooking.repository.CityRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Populates a small sample catalog (city, theater, screen, movie, shows) so the demo profile is
 * immediately browsable. Enabled only when app.demo.seed-sample-data=true (the demo profile).
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.demo.seed-sample-data", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private final CityRepository cityRepository;
    private final CatalogService catalogService;
    private final ShowService showService;

    public DemoDataSeeder(CityRepository cityRepository, CatalogService catalogService, ShowService showService) {
        this.cityRepository = cityRepository;
        this.catalogService = catalogService;
        this.showService = showService;
    }

    @Override
    public void run(String... args) {
        if (cityRepository.count() > 0) {
            return;
        }
        long cityId = catalogService.createCity(new CityRequest("Bangalore")).id();
        long theaterId = catalogService.createTheater(
                new TheaterRequest(cityId, "PVR Forum Mall", "Koramangala")).id();
        long screenId = catalogService.createScreen(new ScreenRequest(theaterId, "Screen 1", List.of(
                new SeatRow("A", Seat.SeatType.REGULAR, 8),
                new SeatRow("B", Seat.SeatType.REGULAR, 8),
                new SeatRow("C", Seat.SeatType.PREMIUM, 6)))).id();
        long movieId = catalogService.createMovie(new MovieRequest(
                "Inception", "A thief who steals corporate secrets through dream-sharing.",
                "English", "Sci-Fi", 148, "PG-13")).id();

        LocalDateTime today6pm = LocalDateTime.now().withHour(18).withMinute(0).withSecond(0).withNano(0);
        showService.createShow(new ShowRequest(movieId, screenId,
                today6pm.plusDays(1), today6pm.plusDays(1).plusMinutes(148), new BigDecimal("200")));
        showService.createShow(new ShowRequest(movieId, screenId,
                today6pm.plusDays(3), today6pm.plusDays(3).plusMinutes(148), new BigDecimal("250")));
    }
}
