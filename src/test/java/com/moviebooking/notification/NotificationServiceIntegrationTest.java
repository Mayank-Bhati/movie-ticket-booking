package com.moviebooking.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.moviebooking.booking.BookingDtos.BookingRequest;
import com.moviebooking.booking.BookingDtos.HoldRequest;
import com.moviebooking.booking.BookingService;
import com.moviebooking.booking.HoldService;
import com.moviebooking.catalog.CatalogDtos.CityRequest;
import com.moviebooking.catalog.CatalogDtos.MovieRequest;
import com.moviebooking.catalog.CatalogDtos.ScreenRequest;
import com.moviebooking.catalog.CatalogDtos.SeatRow;
import com.moviebooking.catalog.CatalogDtos.ShowRequest;
import com.moviebooking.catalog.CatalogDtos.TheaterRequest;
import com.moviebooking.catalog.CatalogService;
import com.moviebooking.catalog.ShowService;
import com.moviebooking.entity.Notification;
import com.moviebooking.entity.Seat;
import com.moviebooking.entity.User;
import com.moviebooking.notification.NotificationDtos.NotificationResponse;
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
class NotificationServiceIntegrationTest {

    @Autowired
    private CatalogService catalogService;
    @Autowired
    private ShowService showService;
    @Autowired
    private HoldService holdService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private ShowSeatRepository showSeatRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void bookingRecordsConfirmationDispatchesItAndGeneratesReminder() {
        long showId = seedShow();
        long userId = seedUser("notify@example.com");
        Long seatId = showSeatRepository.findByShowIdOrderByIdAsc(showId).get(0).getId();

        long holdId = holdService.createHold(userId, new HoldRequest(showId, List.of(seatId))).holdId();
        bookingService.confirmBooking(userId, new BookingRequest(holdId, null));

        // Confirmation recorded as PENDING.
        List<NotificationResponse> afterBooking = notificationService.listForUser(userId);
        assertThat(afterBooking).hasSize(1);
        assertThat(afterBooking.get(0).type()).isEqualTo(Notification.Type.BOOKING_CONFIRMED.name());
        assertThat(afterBooking.get(0).status()).isEqualTo(Notification.Status.PENDING.name());

        // Dispatcher delivers it.
        int dispatched = notificationService.dispatchPending();
        assertThat(dispatched).isGreaterThanOrEqualTo(1);
        assertThat(notificationService.listForUser(userId).get(0).status())
                .isEqualTo(Notification.Status.SENT.name());

        // Reminder generated once, then deduplicated.
        int created = notificationService.generateShowReminders(LocalDateTime.now(), 48);
        assertThat(created).isEqualTo(1);
        int createdAgain = notificationService.generateShowReminders(LocalDateTime.now(), 48);
        assertThat(createdAgain).isZero();

        long reminders = notificationService.listForUser(userId).stream()
                .filter(n -> n.type().equals(Notification.Type.SHOW_REMINDER.name()))
                .count();
        assertThat(reminders).isEqualTo(1);
    }

    private long seedShow() {
        long cityId = catalogService.createCity(new CityRequest("Bengaluru-Notify")).id();
        long theaterId = catalogService.createTheater(new TheaterRequest(cityId, "PVR", "MG Road")).id();
        long screenId = catalogService.createScreen(new ScreenRequest(theaterId, "Screen 1",
                List.of(new SeatRow("A", Seat.SeatType.REGULAR, 3)))).id();
        long movieId = catalogService.createMovie(new MovieRequest("Dune", null, "English", "SciFi", 155, "PG")).id();
        return showService.createShow(new ShowRequest(movieId, screenId,
                LocalDateTime.now().plusHours(10), LocalDateTime.now().plusHours(12),
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
