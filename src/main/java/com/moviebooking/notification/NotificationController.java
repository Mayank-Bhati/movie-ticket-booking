package com.moviebooking.notification;

import com.moviebooking.notification.NotificationDtos.NotificationResponse;
import com.moviebooking.security.AppUserPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Returns the current user's notifications (confirmations, cancellations, reminders), newest first. */
    @GetMapping("/api/notifications")
    public List<NotificationResponse> myNotifications(@AuthenticationPrincipal AppUserPrincipal user) {
        return notificationService.listForUser(user.getId());
    }
}
