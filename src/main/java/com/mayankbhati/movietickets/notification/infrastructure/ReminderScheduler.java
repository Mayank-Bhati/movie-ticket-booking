package com.mayankbhati.movietickets.notification.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mayankbhati.movietickets.notification.application.OutboxService;
import com.mayankbhati.movietickets.booking.domain.Booking;
import com.mayankbhati.movietickets.booking.infrastructure.BookingRepository;
import com.mayankbhati.movietickets.identity.domain.UserAccount;
import com.mayankbhati.movietickets.identity.infrastructure.UserAccountRepository;

@Component
class ReminderScheduler {
    private final BookingRepository bookings;
    private final UserAccountRepository users;
    private final OutboxService outbox;
    private final Clock clock;

    ReminderScheduler(BookingRepository bookings, UserAccountRepository users,
                      OutboxService outbox, Clock clock) {
        this.bookings = bookings;
        this.users = users;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${booking.reminder-delay-ms:300000}")
    @Transactional
    void enqueueUpcomingShowReminders() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        var upcoming = bookings.findByStatusAndShowingStartsAtGreaterThanEqualAndShowingStartsAtLessThan(
                "CONFIRMED", now.plusMinutes(55), now.plusMinutes(65));
        var customers = users.findAllById(upcoming.stream().map(Booking::getCustomerId).toList()).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        for (Booking booking : upcoming) {
            UserAccount customer = customers.get(booking.getCustomerId());
            Reminder reminder = new Reminder(booking.getId(), customer.getEmail(),
                    booking.getShowing().getMovie().getTitle(), booking.getShowing().getStartsAt());
            outbox.enqueue(reminder.bookingId(), "SHOW_REMINDER", reminder,
                    "show-reminder:" + reminder.bookingId());
        }
    }

    private record Reminder(String bookingId, String email, String movieTitle, OffsetDateTime startsAt) { }
}
