package com.moviebooking.notification;

import java.time.LocalDateTime;

public class NotificationDtos {

    public record NotificationResponse(Long id, String type, String payload, String status,
                                       LocalDateTime createdAt, LocalDateTime sentAt) {
    }
}
