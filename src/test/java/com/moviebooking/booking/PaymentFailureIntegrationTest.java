package com.moviebooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.moviebooking.booking.BookingDtos.BookingRequest;
import com.moviebooking.booking.BookingDtos.HoldRequest;
import com.moviebooking.booking.PaymentGateway.PaymentResult;
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
import com.moviebooking.web.ApiException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Verifies that a failed payment rolls the booking back and leaves the hold and seats intact. */
@SpringBootTest
class PaymentFailureIntegrationTest {

    @MockitoBean
    private PaymentGateway paymentGateway;

    @Autowired
    private CatalogService catalogService;
    @Autowired
    private ShowService showService;
    @Autowired
    private HoldService holdService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private SeatHoldRepository seatHoldRepository;
    @Autowired
    private ShowSeatRepository showSeatRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void failedPaymentRollsBackBookingAndKeepsHold() {
        when(paymentGateway.charge(any(), anyString()))
                .thenReturn(new PaymentResult(false, null));

        long showId = seedShow();
        long userId = seedUser();
        Long seatId = showSeatRepository.findByShowIdOrderByIdAsc(showId).get(0).getId();
        long holdId = holdService.createHold(userId, new HoldRequest(showId, List.of(seatId))).holdId();

        assertThatThrownBy(() -> bookingService.confirmBooking(userId, new BookingRequest(holdId, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Payment failed");

        // The booking transaction rolled back: the seat is still held and the hold is still active.
        assertThat(showSeatRepository.findById(seatId).orElseThrow().getStatus())
                .isEqualTo(ShowSeat.Status.HELD);
        assertThat(seatHoldRepository.findById(holdId).orElseThrow().getStatus())
                .isEqualTo(SeatHold.Status.ACTIVE);
    }

    private long seedShow() {
        long cityId = catalogService.createCity(new CityRequest("Bengaluru-PayFail")).id();
        long theaterId = catalogService.createTheater(new TheaterRequest(cityId, "PVR", "MG Road")).id();
        long screenId = catalogService.createScreen(new ScreenRequest(theaterId, "Screen 1",
                List.of(new SeatRow("A", Seat.SeatType.REGULAR, 3)))).id();
        long movieId = catalogService.createMovie(new MovieRequest("Dune", null, "English", "SciFi", 155, "PG")).id();
        return showService.createShow(new ShowRequest(movieId, screenId,
                LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(3).plusHours(2),
                new BigDecimal("200"))).id();
    }

    private long seedUser() {
        User user = new User();
        user.setEmail("payfail@example.com");
        user.setPassword("x");
        user.setFullName("User");
        user.setRole(User.Role.CUSTOMER);
        return userRepository.save(user).getId();
    }
}
