package com.moviebooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.moviebooking.booking.BookingDtos.HoldRequest;
import com.moviebooking.catalog.CatalogDtos.CityRequest;
import com.moviebooking.catalog.CatalogDtos.MovieRequest;
import com.moviebooking.catalog.CatalogDtos.ScreenRequest;
import com.moviebooking.catalog.CatalogDtos.SeatRow;
import com.moviebooking.catalog.CatalogDtos.ShowRequest;
import com.moviebooking.catalog.CatalogDtos.TheaterRequest;
import com.moviebooking.catalog.CatalogService;
import com.moviebooking.catalog.ShowService;
import com.moviebooking.entity.Seat;
import com.moviebooking.entity.ShowSeat;
import com.moviebooking.entity.User;
import com.moviebooking.repository.ShowSeatRepository;
import com.moviebooking.repository.UserRepository;
import com.moviebooking.web.ApiException;
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
class SeatBookingConcurrencyIntegrationTest {

    @Autowired
    private CatalogService catalogService;
    @Autowired
    private ShowService showService;
    @Autowired
    private HoldService holdService;
    @Autowired
    private ShowSeatRepository showSeatRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void onlyOneOfManyConcurrentHoldsWinsTheSameSeat() throws Exception {
        long showId = seedShow();
        Long seatId = showSeatRepository.findByShowIdOrderByIdAsc(showId).get(0).getId();
        List<Long> userIds = seedUsers(20);

        int threads = userIds.size();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        for (Long userId : userIds) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    holdService.createHold(userId, new HoldRequest(showId, List.of(seatId)));
                    wins.incrementAndGet();
                } catch (ApiException ex) {
                    conflicts.incrementAndGet();
                } catch (Exception ignored) {
                    // lock timeout / rollback races also count as "did not win"
                } finally {
                    done.countDown();
                }
            });
        }

        startGate.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(wins.get()).as("exactly one hold should succeed").isEqualTo(1);
        assertThat(conflicts.get()).as("everyone else is rejected").isGreaterThanOrEqualTo(threads - 1);

        ShowSeat seat = showSeatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(ShowSeat.Status.HELD);
    }

    private long seedShow() {
        long cityId = catalogService.createCity(new CityRequest("Bengaluru-Concurrency")).id();
        long theaterId = catalogService.createTheater(new TheaterRequest(cityId, "PVR", "MG Road")).id();
        long screenId = catalogService.createScreen(new ScreenRequest(theaterId, "Screen 1",
                List.of(new SeatRow("A", Seat.SeatType.REGULAR, 5)))).id();
        long movieId = catalogService.createMovie(new MovieRequest("Dune", null, "English", "SciFi", 155, "PG")).id();
        return showService.createShow(new ShowRequest(movieId, screenId,
                LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(3).plusHours(2),
                new BigDecimal("200"))).id();
    }

    private List<Long> seedUsers(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> {
            User user = new User();
            user.setEmail("racer" + i + "@example.com");
            user.setPassword("x");
            user.setFullName("Racer " + i);
            user.setRole(User.Role.CUSTOMER);
            return userRepository.save(user).getId();
        }).toList();
    }
}
