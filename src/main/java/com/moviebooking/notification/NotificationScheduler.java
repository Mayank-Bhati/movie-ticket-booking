package com.moviebooking.notification;

import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the notification outbox off the booking request thread: a dispatcher delivers pending
 * notifications, and a reminder job enqueues reminders for shows starting soon.
 */
@Component
public class NotificationScheduler {

    private final NotificationService notificationService;
    private final long reminderWindowHours;

    public NotificationScheduler(NotificationService notificationService,
                                 @Value("${app.notification.reminder-window-hours:24}") long reminderWindowHours) {
        this.notificationService = notificationService;
        this.reminderWindowHours = reminderWindowHours;
    }

    @Scheduled(fixedDelayString = "${app.notification.dispatch-interval-ms:5000}")
    public void dispatch() {
        notificationService.dispatchPending();
    }

    @Scheduled(fixedDelayString = "${app.notification.reminder-interval-ms:3600000}")
    public void reminders() {
        notificationService.generateShowReminders(LocalDateTime.now(), reminderWindowHours);
    }
}
