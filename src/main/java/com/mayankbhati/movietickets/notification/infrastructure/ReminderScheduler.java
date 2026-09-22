package com.mayankbhati.movietickets.notification.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mayankbhati.movietickets.notification.application.OutboxService;

@Component
class ReminderScheduler {
    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxService outbox;
    private final Clock clock;

    ReminderScheduler(NamedParameterJdbcTemplate jdbc, OutboxService outbox, Clock clock) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${booking.reminder-delay-ms:300000}")
    @Transactional
    void enqueueUpcomingShowReminders() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Reminder> reminders = jdbc.query("""
                SELECT b.id, u.email, m.title, sh.starts_at
                FROM booking b
                JOIN app_user u ON u.id = b.customer_id
                JOIN showing sh ON sh.id = b.showing_id
                JOIN movie m ON m.id = sh.movie_id
                WHERE b.status = 'CONFIRMED'
                  AND sh.starts_at >= :windowStart AND sh.starts_at < :windowEnd
                """, Map.of("windowStart", now.plusMinutes(55), "windowEnd", now.plusMinutes(65)),
                (rs, rowNum) -> new Reminder(rs.getString("id"), rs.getString("email"),
                        rs.getString("title"), rs.getObject("starts_at", OffsetDateTime.class)));
        for (Reminder reminder : reminders) {
            try {
                outbox.enqueue(reminder.bookingId(), "SHOW_REMINDER", reminder,
                        "show-reminder:" + reminder.bookingId());
            } catch (DuplicateKeyException ignored) {
                // The dedupe key makes the periodic scan safe to repeat.
            }
        }
    }

    private record Reminder(String bookingId, String email, String movieTitle, OffsetDateTime startsAt) {
    }
}

