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
import com.moviebooking.entity.SeatHold;
import com.moviebooking.entity.ShowSeat;
import com.moviebooking.entity.User;
import com.moviebooking.repository.SeatHoldRepository;
import com.moviebooking.repository.ShowSeatRepository;
import com.moviebooking.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class HoldExpiryIntegrationTest {

    @Autowired
    private CatalogService catalogService;
    @Autowired
    private ShowService showService;
    @Autowired
    private HoldService holdService;
    @Autowired
    private HoldExpiryScheduler holdExpiryScheduler;
    @Autowired
    private SeatHoldRepository seatHoldRepository;
    @Autowired
    private ShowSeatRepository showSeatRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void sweeperReleasesExpiredHoldsAndFreesSeats() {
        long showId = seedShow();
        Long seatId = showSeatRepository.findByShowIdOrderByIdAsc(showId).get(0).getId();
        long userId = seedUser("holder@example.com");

        long holdId = holdService.createHold(userId, new HoldRequest(showId, List.of(seatId))).holdId();
        assertThat(showSeatRepository.findById(seatId).orElseThrow().getStatus())
                .isEqualTo(ShowSeat.Status.HELD);

        expireHold(holdId);
        holdExpiryScheduler.releaseExpiredHolds();

        assertThat(seatHoldRepository.findById(holdId).orElseThrow().getStatus())
                .isEqualTo(SeatHold.Status.EXPIRED);
        assertThat(showSeatRepository.findById(seatId).orElseThrow().getStatus())
                .isEqualTo(ShowSeat.Status.AVAILABLE);
    }

    @Test
    void anExpiredHoldCanBeClaimedByAnotherUser() {
        long showId = seedShow();
        Long seatId = showSeatRepository.findByShowIdOrderByIdAsc(showId).get(0).getId();
        long first = seedUser("first@example.com");
        long second = seedUser("second@example.com");

        long firstHold = holdService.createHold(first, new HoldRequest(showId, List.of(seatId))).holdId();
        expireHold(firstHold);

        // Second user claims the same seat directly (lazy reclaim, without waiting for the sweeper).
        long secondHold = holdService.createHold(second, new HoldRequest(showId, List.of(seatId))).holdId();

        ShowSeat seat = showSeatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(ShowSeat.Status.HELD);
        assertThat(seat.getHold().getId()).isEqualTo(secondHold);
    }

    private void expireHold(long holdId) {
        SeatHold hold = seatHoldRepository.findById(holdId).orElseThrow();
        hold.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        seatHoldRepository.save(hold);
    }

    private long seedShow() {
        long cityId = catalogService.createCity(new CityRequest("Bengaluru-Expiry")).id();
        long theaterId = catalogService.createTheater(new TheaterRequest(cityId, "PVR", "MG Road")).id();
        long screenId = catalogService.createScreen(new ScreenRequest(theaterId, "Screen 1",
                List.of(new SeatRow("A", Seat.SeatType.REGULAR, 3)))).id();
        long movieId = catalogService.createMovie(new MovieRequest("Dune", null, "English", "SciFi", 155, "PG")).id();
        return showService.createShow(new ShowRequest(movieId, screenId,
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(2),
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
