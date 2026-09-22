package com.mayankbhati.movietickets.notification.application;

public interface NotificationSender {
    void send(String eventType, String payload);
}

