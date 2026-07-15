package com.moviebooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.moviebooking.booking.BookingDtos.BookingRequest;
import com.moviebooking.booking.BookingDtos.HoldRequest;
import com.moviebooking.catalog.CatalogDtos.CityRequest;
import com.moviebooking.catalog.CatalogDtos.MovieRequest;
import com.moviebooking.catalog.CatalogDtos.ScreenRequest;
import com.moviebooking.catalog.CatalogDtos.SeatRow;
import com.moviebooking.catalog.CatalogDtos.ShowRequest;
import com.moviebooking.catalog.CatalogDtos.TheaterRequest;
import com.moviebooking.catalog.CatalogService;
import com.moviebooking.catalog.ShowService;
import com.moviebooking.entity.DiscountCode;
import com.moviebooking.entity.Seat;
import com.moviebooking.entity.ShowSeat;
import com.moviebooking.entity.User;
import com.moviebooking.pricing.PricingDtos.DiscountCodeRequest;
import com.moviebooking.pricing.DiscountService;
import com.moviebooking.repository.DiscountCodeRepository;
import com.moviebooking.repository.ShowSeatRepository;
import com.moviebooking.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DiscountUsageConcurrencyIntegrationTest {

    @Autowired
    private CatalogService catalogService;
    @Autowired
    private ShowService showService;
    @Autowired
    private HoldService holdService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private DiscountService discountService;
    @Autowired
    private DiscountCodeRepository discountCodeRepository;
    @Autowired
    private ShowSeatRepository showSeatRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void limitedDiscountCodeIsNotRedeemedBeyondItsCap() throws Exception {
        long showId = seedShow();
        discountService.create(new DiscountCodeRequest("LIMITED3", DiscountCode.Type.PERCENT,
                new BigDecimal("10"), null, 3, null, null, true));

        List<ShowSeat> seats = showSeatRepository.findByShowIdOrderByIdAsc(showId);
        int attempts = seats.size(); // 10 seats, each booked by a distinct user with the same code

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);
        AtomicInteger booked = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            long userId = seedUser("disc" + i + "@example.com");
            Long seatId = seats.get(i).getId();
            pool.submit(() -> {
                try {
                    startGate.await();
                    long holdId = holdService.createHold(userId, new HoldRequest(showId, List.of(seatId))).holdId();
                    bookingService.confirmBooking(userId, new BookingRequest(holdId, "LIMITED3"));
                    booked.incrementAndGet();
                } catch (Exception ignored) {
                    // usage-limit rejection is expected once the cap is reached
                } finally {
                    done.countDown();
                }
            });
        }

        startGate.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // The code must never be redeemed more than its cap, even under concurrency.
        assertThat(booked.get()).isEqualTo(3);
        assertThat(discountCodeRepository.findByCodeIgnoreCase("LIMITED3").orElseThrow().getUsedCount())
                .isEqualTo(3);
    }

    private long seedShow() {
        long cityId = catalogService.createCity(new CityRequest("Bengaluru-Discount")).id();
        long theaterId = catalogService.createTheater(new TheaterRequest(cityId, "PVR", "MG Road")).id();
        long screenId = catalogService.createScreen(new ScreenRequest(theaterId, "Screen 1",
                List.of(new SeatRow("A", Seat.SeatType.REGULAR, 10)))).id();
        long movieId = catalogService.createMovie(new MovieRequest("Dune", null, "English", "SciFi", 155, "PG")).id();
        return showService.createShow(new ShowRequest(movieId, screenId,
                LocalDateTime.now().plusDays(4), LocalDateTime.now().plusDays(4).plusHours(2),
                new BigDecimal("200"))).id();
    }

    private long seedUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("x");
        user.setFullName("User");
        user.setRole(User.Role.CUSTOMER);
        return userRepository.save(user).getId();
    }
}
