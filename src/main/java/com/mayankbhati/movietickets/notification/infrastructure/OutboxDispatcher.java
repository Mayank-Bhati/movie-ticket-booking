package com.mayankbhati.movietickets.notification.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mayankbhati.movietickets.notification.application.NotificationSender;

@Component
class OutboxDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final NamedParameterJdbcTemplate jdbc;
    private final NotificationSender sender;
    private final Clock clock;

    OutboxDispatcher(NamedParameterJdbcTemplate jdbc, NotificationSender sender, Clock clock) {
        this.jdbc = jdbc;
        this.sender = sender;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${booking.outbox-dispatch-delay-ms:2000}")
    void dispatch() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Event> events = jdbc.query("""
                SELECT id, event_type, payload FROM outbox_event
                WHERE status = 'PENDING' AND available_at <= :now
                ORDER BY id LIMIT 25
                """, Map.of("now", now), (rs, rowNum) -> new Event(
                rs.getLong("id"), rs.getString("event_type"), rs.getString("payload")));
        for (Event event : events) {
            try {
                sender.send(event.type(), event.payload());
                jdbc.update("""
                        UPDATE outbox_event SET status = 'SENT', sent_at = :now, attempts = attempts + 1
                        WHERE id = :id AND status = 'PENDING'
                        """, Map.of("now", now, "id", event.id()));
            } catch (RuntimeException exception) {
                LOGGER.warn("Notification dispatch failed for outbox event {}", event.id(), exception);
                jdbc.update("""
                        UPDATE outbox_event
                        SET attempts = attempts + 1,
                            status = CASE WHEN attempts >= 4 THEN 'FAILED' ELSE 'PENDING' END,
                            available_at = :retryAt
                        WHERE id = :id
                        """, Map.of("retryAt", now.plusSeconds(30), "id", event.id()));
            }
        }
    }

    private record Event(long id, String type, String payload) {
    }
}

