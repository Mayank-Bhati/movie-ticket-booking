package com.moviebooking.booking;

import com.moviebooking.booking.BookingDtos.HeldSeat;
import com.moviebooking.booking.BookingDtos.HoldRequest;
import com.moviebooking.booking.BookingDtos.HoldResponse;
import com.moviebooking.entity.SeatHold;
import com.moviebooking.entity.Show;
import com.moviebooking.entity.ShowSeat;
import com.moviebooking.repository.SeatHoldRepository;
import com.moviebooking.repository.ShowRepository;
import com.moviebooking.repository.ShowSeatRepository;
import com.moviebooking.repository.UserRepository;
import com.moviebooking.web.ApiException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HoldService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final UserRepository userRepository;
    private final long ttlMinutes;

    public HoldService(ShowRepository showRepository, ShowSeatRepository showSeatRepository,
                       SeatHoldRepository seatHoldRepository, UserRepository userRepository,
                       @Value("${app.hold.ttl-minutes}") long ttlMinutes) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.seatHoldRepository = seatHoldRepository;
        this.userRepository = userRepository;
        this.ttlMinutes = ttlMinutes;
    }

    /**
     * Places a time-bound hold on the requested seats. The show seats are locked FOR UPDATE
     * (ordered by id) so concurrent hold attempts on the same seat are serialized: the first
     * transaction wins, later ones see the seat as HELD/BOOKED and are rejected.
     */
    @Transactional
    public HoldResponse createHold(Long userId, HoldRequest request) {
        Show show = showRepository.findById(request.showId())
                .orElseThrow(() -> ApiException.notFound("Show not found: " + request.showId()));
        List<Long> seatIds = request.showSeatIds().stream().distinct().sorted().toList();

        List<ShowSeat> seats = showSeatRepository.lockByShowAndIds(show.getId(), seatIds);
        if (seats.size() != seatIds.size()) {
            throw ApiException.badRequest("One or more seats do not belong to this show");
        }

        LocalDateTime now = LocalDateTime.now();
        for (ShowSeat seat : seats) {
            if (seat.getStatus() == ShowSeat.Status.BOOKED) {
                throw ApiException.conflict("Seat already booked: " + seat.getSeat().label());
            }
            if (seat.getStatus() == ShowSeat.Status.HELD && isHoldLive(seat, now)) {
                throw ApiException.conflict("Seat is currently held by another user: " + seat.getSeat().label());
            }
        }

        SeatHold hold = new SeatHold();
        hold.setUser(userRepository.getReferenceById(userId));
        hold.setShow(show);
        hold.setStatus(SeatHold.Status.ACTIVE);
        hold.setExpiresAt(now.plusMinutes(ttlMinutes));
        seatHoldRepository.save(hold);

        for (ShowSeat seat : seats) {
            seat.setStatus(ShowSeat.Status.HELD);
            seat.setHold(hold);
        }
        return toResponse(hold, seats);
    }

    private boolean isHoldLive(ShowSeat seat, LocalDateTime now) {
        SeatHold hold = seat.getHold();
        return hold != null
                && hold.getStatus() == SeatHold.Status.ACTIVE
                && hold.getExpiresAt().isAfter(now);
    }

    private HoldResponse toResponse(SeatHold hold, List<ShowSeat> seats) {
        List<HeldSeat> heldSeats = seats.stream()
                .map(ss -> new HeldSeat(ss.getId(), ss.getSeat().label(), ss.getPrice()))
                .toList();
        BigDecimal subtotal = heldSeats.stream()
                .map(HeldSeat::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new HoldResponse(hold.getId(), hold.getShow().getId(), hold.getStatus().name(),
                hold.getExpiresAt(), heldSeats, subtotal);
    }
}
