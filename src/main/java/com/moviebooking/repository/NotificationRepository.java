package com.moviebooking.repository;

import com.moviebooking.entity.Notification;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByStatusOrderByCreatedAtAsc(Notification.Status status, Limit limit);

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByBookingIdAndType(Long bookingId, Notification.Type type);
}
