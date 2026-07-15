package com.moviebooking.notification;

import com.moviebooking.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Default sender that logs the notification, standing in for a real email/SMS transport. */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(Notification notification) {
        log.info("Sending {} notification to {}: {}",
                notification.getType(), notification.getUser().getEmail(), notification.getPayload());
    }
}
