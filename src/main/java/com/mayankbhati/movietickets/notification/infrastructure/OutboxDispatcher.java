package com.mayankbhati.movietickets.notification.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mayankbhati.movietickets.notification.application.NotificationSender;
import com.mayankbhati.movietickets.notification.domain.OutboxEvent;

@Component
class OutboxDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final OutboxStore store;
    private final NotificationSender sender;
    private final Clock clock;

    OutboxDispatcher(OutboxStore store, NotificationSender sender, Clock clock) {
        this.store = store;
        this.sender = sender;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${booking.outbox-dispatch-delay-ms:2000}")
    @Transactional
    void dispatch() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (OutboxEvent event : store.lockPendingBatch(now, 25)) {
            try {
                sender.send(event.getEventType(), event.getPayload());
                event.markSent(now);
            } catch (RuntimeException exception) {
                LOGGER.warn("Notification dispatch failed for outbox event {}", event.getId(), exception);
                event.markFailedAttempt(now.plusSeconds(30));
            }
        }
    }
}
