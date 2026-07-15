package com.moviebooking.notification;

import com.moviebooking.entity.Notification;

/**
 * Delivery channel for notifications. Abstracted so the outbox dispatcher does not depend on a
 * concrete transport; a real deployment would swap in an email/SMS/push implementation.
 */
public interface NotificationSender {

    void send(Notification notification);
}
