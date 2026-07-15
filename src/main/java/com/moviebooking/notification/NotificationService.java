package com.moviebooking.notification;

import com.moviebooking.entity.Booking;
import com.moviebooking.entity.Notification;
import com.moviebooking.notification.NotificationDtos.NotificationResponse;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.NotificationRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the transactional outbox pattern: booking-related notifications are written as
 * PENDING rows inside the same transaction as the booking change (so they are atomic with it and
 * never block the caller), and a background dispatcher later delivers them.
 */
@Service
public class NotificationService {

    private static final int DISPATCH_BATCH = 50;

    private final NotificationRepository notificationRepository;
    private final BookingRepository bookingRepository;
    private final NotificationSender sender;

    public NotificationService(NotificationRepository notificationRepository,
                               BookingRepository bookingRepository, NotificationSender sender) {
        this.notificationRepository = notificationRepository;
        this.bookingRepository = bookingRepository;
        this.sender = sender;
    }

    /** Records a confirmation notification. Called within the booking transaction. */
    public void recordBookingConfirmed(Booking booking) {
        String payload = "Booking %s confirmed for '%s' — %d seat(s), total %s"
                .formatted(booking.getBookingRef(), booking.getShow().getMovie().getTitle(),
                        booking.getSeats().size(), booking.getTotalAmount());
        save(booking, Notification.Type.BOOKING_CONFIRMED, payload);
    }

    /** Records a cancellation notification. Called within the cancellation transaction. */
    public void recordBookingCancelled(Booking booking, BigDecimal refundAmount) {
        String payload = "Booking %s cancelled — refund of %s will be processed"
                .formatted(booking.getBookingRef(), refundAmount);
        save(booking, Notification.Type.BOOKING_CANCELLED, payload);
    }

    private void save(Booking booking, Notification.Type type, String payload) {
        Notification notification = new Notification();
        notification.setUser(booking.getUser());
        notification.setBooking(booking);
        notification.setType(type);
        notification.setPayload(payload);
        notification.setStatus(Notification.Status.PENDING);
        notificationRepository.save(notification);
    }

    /** Delivers a batch of pending notifications. Invoked by the background scheduler. */
    @Transactional
    public int dispatchPending() {
        List<Notification> pending = notificationRepository.findByStatusOrderByCreatedAtAsc(
                Notification.Status.PENDING, Limit.of(DISPATCH_BATCH));
        for (Notification notification : pending) {
            try {
                sender.send(notification);
                notification.setStatus(Notification.Status.SENT);
                notification.setSentAt(LocalDateTime.now());
            } catch (RuntimeException ex) {
                notification.setStatus(Notification.Status.FAILED);
            }
        }
        return pending.size();
    }

    /** Creates one reminder per confirmed booking whose show starts within the window. */
    @Transactional
    public int generateShowReminders(LocalDateTime now, long windowHours) {
        List<Booking> upcoming = bookingRepository.findConfirmedWithShowStartingBetween(
                now, now.plusHours(windowHours));
        int created = 0;
        for (Booking booking : upcoming) {
            if (notificationRepository.existsByBookingIdAndType(
                    booking.getId(), Notification.Type.SHOW_REMINDER)) {
                continue;
            }
            String payload = "Reminder: '%s' starts at %s — booking %s"
                    .formatted(booking.getShow().getMovie().getTitle(),
                            booking.getShow().getStartsAt(), booking.getBookingRef());
            save(booking, Notification.Type.SHOW_REMINDER, payload);
            created++;
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getType().name(), n.getPayload(),
                        n.getStatus().name(), n.getCreatedAt(), n.getSentAt()))
                .toList();
    }
}
