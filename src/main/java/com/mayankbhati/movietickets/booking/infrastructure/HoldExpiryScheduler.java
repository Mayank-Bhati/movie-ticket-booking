package com.mayankbhati.movietickets.booking.infrastructure;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mayankbhati.movietickets.booking.application.BookingService;

@Component
class HoldExpiryScheduler {
    private final BookingService bookings;

    HoldExpiryScheduler(BookingService bookings) {
        this.bookings = bookings;
    }

    @Scheduled(fixedDelayString = "${booking.expiry-sweep-delay-ms:30000}")
    void releaseExpiredHolds() {
        bookings.releaseExpiredHolds();
    }
}

