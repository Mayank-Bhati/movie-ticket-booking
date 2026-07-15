package com.moviebooking.booking;

import com.moviebooking.entity.SeatHold;
import com.moviebooking.entity.ShowSeat;
import com.moviebooking.repository.SeatHoldRepository;
import com.moviebooking.repository.ShowSeatRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background sweep that releases expired holds. Seats are also reclaimed lazily when another user
 * tries to hold them; this sweeper guarantees they are freed even if no one asks, so a seat map
 * reflects reality shortly after a hold lapses.
 */
@Component
public class HoldExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryScheduler.class);

    private final SeatHoldRepository seatHoldRepository;
    private final ShowSeatRepository showSeatRepository;

    public HoldExpiryScheduler(SeatHoldRepository seatHoldRepository, ShowSeatRepository showSeatRepository) {
        this.seatHoldRepository = seatHoldRepository;
        this.showSeatRepository = showSeatRepository;
    }

    @Scheduled(fixedDelayString = "${app.hold.sweep-interval-ms:30000}")
    @Transactional
    public void releaseExpiredHolds() {
        List<SeatHold> expired = seatHoldRepository.findByStatusAndExpiresAtBefore(
                SeatHold.Status.ACTIVE, LocalDateTime.now());
        if (expired.isEmpty()) {
            return;
        }
        for (SeatHold hold : expired) {
            hold.setStatus(SeatHold.Status.EXPIRED);
            for (ShowSeat seat : showSeatRepository.findByHoldId(hold.getId())) {
                if (seat.getStatus() == ShowSeat.Status.HELD) {
                    seat.setStatus(ShowSeat.Status.AVAILABLE);
                    seat.setHold(null);
                }
            }
        }
        log.info("Released {} expired hold(s)", expired.size());
    }
}
