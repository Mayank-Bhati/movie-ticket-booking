package com.mayankbhati.movietickets.notification.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mayankbhati.movietickets.notification.application.OutboxService;
import com.mayankbhati.movietickets.booking.infrastructure.BookingStore;

@Component
class ReminderScheduler {
    private final BookingStore bookings;
    private final OutboxService outbox;
    private final Clock clock;

    ReminderScheduler(BookingStore bookings, OutboxService outbox, Clock clock) {
        this.bookings = bookings;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${booking.reminder-delay-ms:300000}")
    @Transactional
    void enqueueUpcomingShowReminders() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (BookingStore.Reminder reminder : bookings.upcomingReminders(
                now.plusMinutes(55), now.plusMinutes(65))) {
            outbox.enqueue(reminder.bookingId(), "SHOW_REMINDER", reminder,
                    "show-reminder:" + reminder.bookingId());
        }
    }
}
