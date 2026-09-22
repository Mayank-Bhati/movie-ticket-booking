package com.mayankbhati.movietickets.notification.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.mayankbhati.movietickets.notification.application.NotificationSender;

@Component
class LoggingNotificationSender implements NotificationSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(String eventType, String payload) {
        LOGGER.info("notification type={} payload={}", eventType, payload);
    }
}

