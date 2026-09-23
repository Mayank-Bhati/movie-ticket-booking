package com.mayankbhati.movietickets.booking.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mayankbhati.movietickets.booking.domain.Booking;
import com.mayankbhati.movietickets.booking.domain.BookingItem;
import com.mayankbhati.movietickets.booking.domain.Payment;
import com.mayankbhati.movietickets.booking.domain.Refund;
import com.mayankbhati.movietickets.booking.domain.SeatHold;
import com.mayankbhati.movietickets.booking.infrastructure.BookingStore;
import com.mayankbhati.movietickets.catalog.domain.DiscountCode;
import com.mayankbhati.movietickets.catalog.domain.ShowSeat;
import com.mayankbhati.movietickets.catalog.domain.Showing;
import com.mayankbhati.movietickets.catalog.infrastructure.CatalogStore;
import com.mayankbhati.movietickets.identity.domain.Actor;
import com.mayankbhati.movietickets.notification.application.OutboxService;
import com.mayankbhati.movietickets.shared.ApiException;

@Service
public class BookingService {
    private final BookingStore store;
    private final CatalogStore catalog;
    private final PaymentGateway payments;
    private final OutboxService outbox;
    private final Clock clock;
    private final Duration holdTtl;

    public BookingService(BookingStore store, CatalogStore catalog, PaymentGateway payments,
                          OutboxService outbox, Clock clock,
                          @Value("${booking.hold-ttl:PT5M}") Duration holdTtl) {
        this.store = store;
        this.catalog = catalog;
        this.payments = payments;
        this.outbox = outbox;
        this.clock = clock;
        this.holdTtl = holdTtl;
    }

    @Transactional
    public HoldView createHold(Actor customer, long showingId, List<Long> requestedSeatIds) {
        LinkedHashSet<Long> uniqueSeatIds = new LinkedHashSet<>(requestedSeatIds);
        if (uniqueSeatIds.isEmpty() || uniqueSeatIds.size() > 10) {
            throw ApiException.badRequest("INVALID_SEAT_COUNT", "Select between 1 and 10 unique seats");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        store.releaseExpired(now);
        Showing showing = catalog.showing(showingId);
        if (showing == null) {
            throw ApiException.notFound("SHOWING_NOT_FOUND", "Showing not found");
        }
        if (!"SCHEDULED".equals(showing.getStatus()) || !showing.getStartsAt().isAfter(now)) {
            throw ApiException.conflict("SHOWING_UNAVAILABLE", "The showing is no longer bookable");
        }

        List<ShowSeat> seats = store.lockSeats(showingId, uniqueSeatIds);
        if (seats.size() != uniqueSeatIds.size()) {
            throw ApiException.notFound("SEAT_NOT_FOUND", "One or more seats do not belong to this showing");
        }
        List<String> unavailable = seats.stream().filter(seat -> !"AVAILABLE".equals(seat.getStatus()))
                .map(seat -> seat.getSeat().label()).toList();
        if (!unavailable.isEmpty()) {
            throw ApiException.conflict("SEAT_UNAVAILABLE", "Seats are no longer available: " + unavailable);
        }

        String holdId = UUID.randomUUID().toString();
        OffsetDateTime expiresAt = now.plus(holdTtl);
        SeatHold hold = new SeatHold(holdId, customer.id(), showing, expiresAt, now);
        seats.forEach(seat -> {
            hold.addItem(seat);
            seat.hold(holdId, expiresAt);
        });
        store.persist(hold);
        return new HoldView(holdId, showingId, labels(seats), subtotal(seats), expiresAt, "ACTIVE");
    }

    @Transactional
    public BookingView confirm(Actor customer, String holdId, String discountCode,
                               String paymentMethod, String idempotencyKey) {
        var existing = store.byIdempotencyKey(customer.id(), idempotencyKey);
        if (existing.isPresent()) {
            return booking(customer.id(), existing.get().getId());
        }
        SeatHold hold = store.lockHold(holdId)
                .orElseThrow(() -> ApiException.notFound("HOLD_NOT_FOUND", "Hold not found"));
        if (hold.getCustomerId() != customer.id()) {
            throw ApiException.notFound("HOLD_NOT_FOUND", "Hold not found");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!"ACTIVE".equals(hold.getStatus()) || !hold.getExpiresAt().isAfter(now)) {
            expireHold(hold);
            throw ApiException.gone("HOLD_EXPIRED", "The seat hold has expired");
        }
        List<ShowSeat> seats = store.lockSeatsForHold(holdId);
        if (seats.isEmpty() || seats.stream().anyMatch(seat -> !"HELD".equals(seat.getStatus()))) {
            throw ApiException.conflict("HOLD_INVALID", "The held inventory is no longer valid");
        }

        long subtotal = subtotal(seats);
        DiscountCode discount = discountCode == null || discountCode.isBlank()
                ? null : lockAndValidateDiscount(discountCode, subtotal, now);
        long discountAmount = discount == null ? 0 : discount.discountFor(subtotal);
        long total = subtotal - discountAmount;
        PaymentGateway.PaymentReceipt receipt = payments.capture(total, "INR", paymentMethod,
                customer.id() + ":" + idempotencyKey);

        String bookingId = UUID.randomUUID().toString();
        Booking booking = new Booking(bookingId, customer.id(), hold.getShowing(), hold, idempotencyKey,
                subtotal, discountAmount, discount == null ? null : discount.getCode(), now);
        seats.forEach(seat -> {
            booking.addItem(seat);
            seat.book(bookingId);
        });
        hold.convert();
        if (discount != null) discount.redeem();
        store.persist(booking);
        store.persist(new Payment(UUID.randomUUID().toString(), booking, total, receipt.reference(), now));
        outbox.enqueue(bookingId, "BOOKING_CONFIRMED",
                Map.of("bookingId", bookingId, "email", customer.email(),
                        "seatLabels", labels(seats), "totalCents", total),
                "booking-confirmed:" + bookingId);
        return toView(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingSummary> history(Actor customer) {
        return store.history(customer.id()).stream().map(booking -> new BookingSummary(
                booking.getId(), booking.getStatus(), booking.getShowing().getMovie().getTitle(),
                booking.getShowing().getStartsAt(), booking.getTotalCents(), booking.getCurrency(),
                booking.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public BookingView booking(long customerId, String bookingId) {
        return store.booking(customerId, bookingId).map(this::toView)
                .orElseThrow(() -> ApiException.notFound("BOOKING_NOT_FOUND", "Booking not found"));
    }

    @Transactional
    public CancellationView cancel(Actor customer, String bookingId) {
        Booking booking = store.lockBooking(bookingId)
                .orElseThrow(() -> ApiException.notFound("BOOKING_NOT_FOUND", "Booking not found"));
        if (booking.getCustomerId() != customer.id()) {
            throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking not found");
        }
        if (!"CONFIRMED".equals(booking.getStatus())) {
            throw ApiException.conflict("BOOKING_ALREADY_CANCELLED", "Booking is already cancelled");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        long minutesBefore = Math.max(0, Duration.between(now, booking.getShowing().getStartsAt()).toMinutes());
        int refundPercent = store.refundPercent(booking.getShowing().getRefundPolicy().getId(), minutesBefore);
        long refundAmount = Math.multiplyExact(booking.getTotalCents(), refundPercent) / 100;
        Payment payment = store.payment(bookingId);
        String refundReference = refundAmount == 0 ? null : payments.refund(payment.getProviderReference(),
                refundAmount, "cancel:" + bookingId);

        booking.cancel(now);
        store.lockSeatsForBooking(bookingId).forEach(ShowSeat::release);
        store.persistRefund(new Refund(UUID.randomUUID().toString(), booking, refundAmount,
                refundPercent, refundReference, now));
        if (refundAmount > 0) payment.markRefunded();
        outbox.enqueue(bookingId, "BOOKING_CANCELLED",
                Map.of("bookingId", bookingId, "email", customer.email(),
                        "refundCents", refundAmount, "refundPercent", refundPercent),
                "booking-cancelled:" + bookingId);
        return new CancellationView(bookingId, "CANCELLED", refundAmount, refundPercent, "INR");
    }

    @Transactional
    public int releaseExpiredHolds() {
        return store.releaseExpired(OffsetDateTime.now(clock));
    }

    private DiscountCode lockAndValidateDiscount(String requestedCode, long subtotal, OffsetDateTime now) {
        DiscountCode discount = store.lockDiscount(requestedCode.toUpperCase())
                .orElseThrow(() -> ApiException.badRequest("DISCOUNT_INVALID", "Discount code is invalid"));
        if (!discount.isApplicable(subtotal, now)) {
            throw ApiException.badRequest("DISCOUNT_NOT_APPLICABLE", "Discount code is not applicable");
        }
        return discount;
    }

    private void expireHold(SeatHold hold) {
        hold.expire();
        store.lockSeatsForHold(hold.getId()).forEach(ShowSeat::release);
    }

    private BookingView toView(Booking booking) {
        List<String> seatLabels = booking.getItems().stream().map(BookingItem::getSeatLabel).sorted().toList();
        return new BookingView(booking.getId(), booking.getStatus(), booking.getShowing().getId(),
                booking.getShowing().getMovie().getTitle(), booking.getShowing().getStartsAt(), seatLabels,
                booking.getSubtotalCents(), booking.getDiscountCents(), booking.getTotalCents(),
                booking.getCurrency(), booking.getDiscountCode(), booking.getCreatedAt(), booking.getCancelledAt());
    }

    private static long subtotal(List<ShowSeat> seats) {
        return seats.stream().mapToLong(ShowSeat::getPriceCents).sum();
    }

    private static List<String> labels(List<ShowSeat> seats) {
        return seats.stream().map(seat -> seat.getSeat().label()).toList();
    }

    public record HoldView(String holdId, long showingId, List<String> seatLabels,
                           long subtotalCents, OffsetDateTime expiresAt, String status) { }
    public record BookingView(String id, String status, long showingId, String movieTitle,
                              OffsetDateTime startsAt, List<String> seatLabels, long subtotalCents,
                              long discountCents, long totalCents, String currency, String discountCode,
                              OffsetDateTime createdAt, OffsetDateTime cancelledAt) { }
    public record BookingSummary(String id, String status, String movieTitle, OffsetDateTime startsAt,
                                 long totalCents, String currency, OffsetDateTime createdAt) { }
    public record CancellationView(String bookingId, String status, long refundCents,
                                   int refundPercent, String currency) { }
}
