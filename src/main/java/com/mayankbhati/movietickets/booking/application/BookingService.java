package com.mayankbhati.movietickets.booking.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mayankbhati.movietickets.booking.domain.Booking;
import com.mayankbhati.movietickets.booking.domain.BookingItem;
import com.mayankbhati.movietickets.booking.domain.Payment;
import com.mayankbhati.movietickets.booking.domain.Refund;
import com.mayankbhati.movietickets.booking.domain.SeatHold;
import com.mayankbhati.movietickets.booking.infrastructure.BookingRepository;
import com.mayankbhati.movietickets.booking.infrastructure.PaymentRepository;
import com.mayankbhati.movietickets.booking.infrastructure.RefundRepository;
import com.mayankbhati.movietickets.booking.infrastructure.SeatHoldRepository;
import com.mayankbhati.movietickets.catalog.domain.DiscountCode;
import com.mayankbhati.movietickets.catalog.domain.ShowSeat;
import com.mayankbhati.movietickets.catalog.domain.Showing;
import com.mayankbhati.movietickets.catalog.infrastructure.DiscountCodeRepository;
import com.mayankbhati.movietickets.catalog.infrastructure.RefundRuleRepository;
import com.mayankbhati.movietickets.catalog.infrastructure.ShowSeatRepository;
import com.mayankbhati.movietickets.catalog.infrastructure.ShowingRepository;
import com.mayankbhati.movietickets.identity.domain.Actor;
import com.mayankbhati.movietickets.notification.application.OutboxService;
import com.mayankbhati.movietickets.shared.ApiException;

@Service
public class BookingService {
    private final BookingRepository bookings;
    private final SeatHoldRepository holds;
    private final PaymentRepository paymentRecords;
    private final RefundRepository refunds;
    private final ShowingRepository showings;
    private final ShowSeatRepository inventory;
    private final DiscountCodeRepository discounts;
    private final RefundRuleRepository refundRules;
    private final PaymentGateway payments;
    private final OutboxService outbox;
    private final Clock clock;
    private final Duration holdTtl;

    public BookingService(BookingRepository bookings, SeatHoldRepository holds,
                          PaymentRepository paymentRecords, RefundRepository refunds,
                          ShowingRepository showings, ShowSeatRepository inventory,
                          DiscountCodeRepository discounts, RefundRuleRepository refundRules,
                          PaymentGateway payments, OutboxService outbox, Clock clock,
                          @Value("${booking.hold-ttl:PT5M}") Duration holdTtl) {
        this.bookings = bookings;
        this.holds = holds;
        this.paymentRecords = paymentRecords;
        this.refunds = refunds;
        this.showings = showings;
        this.inventory = inventory;
        this.discounts = discounts;
        this.refundRules = refundRules;
        this.payments = payments;
        this.outbox = outbox;
        this.clock = clock;
        this.holdTtl = holdTtl;
    }

    @Transactional
    public HoldView createHold(Actor customer, long showingId, List<Long> requestedSeatIds) {
        LinkedHashSet<Long> uniqueSeatIds = validateSeatSelection(requestedSeatIds);
        OffsetDateTime now = OffsetDateTime.now(clock);
        releaseExpiredHolds(now);
        Showing showing = requireBookableShowing(showingId, now);
        List<ShowSeat> seats = lockAvailableSeats(showingId, uniqueSeatIds);
        SeatHold hold = buildHold(customer.id(), showing, seats, now);
        holds.save(hold);
        return toHoldView(hold, seats);
    }

    @Transactional
    public BookingView confirm(Actor customer, String holdId, String discountCode,
                               String paymentMethod, String idempotencyKey) {
        Optional<BookingView> existing = findExistingBooking(customer.id(), idempotencyKey);
        if (existing.isPresent()) return existing.get();

        OffsetDateTime now = OffsetDateTime.now(clock);
        SeatHold hold = lockActiveHold(holdId, customer.id(), now);
        List<ShowSeat> seats = lockHeldSeats(holdId);
        Price price = calculatePrice(seats, discountCode, now);
        PaymentGateway.PaymentReceipt receipt = capturePayment(customer.id(), idempotencyKey, paymentMethod, price);
        Booking booking = buildBooking(customer.id(), idempotencyKey, hold, seats, price, now);
        completeBooking(booking, hold, seats, price.discount(), receipt, now);
        enqueueConfirmation(booking, customer.email(), seats);
        return toView(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingSummary> history(Actor customer) {
        return bookings.findByCustomerIdOrderByCreatedAtDesc(customer.id()).stream()
                .map(booking -> new BookingSummary(
                booking.getId(), booking.getStatus(), booking.getShowing().getMovie().getTitle(),
                booking.getShowing().getStartsAt(), booking.getTotalCents(), booking.getCurrency(),
                booking.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public BookingView booking(long customerId, String bookingId) {
        return bookings.findDetailedByIdAndCustomerId(bookingId, customerId).map(this::toView)
                .orElseThrow(() -> ApiException.notFound("BOOKING_NOT_FOUND", "Booking not found"));
    }

    @Transactional
    public CancellationView cancel(Actor customer, String bookingId) {
        Booking booking = lockCancellableBooking(bookingId, customer.id());
        OffsetDateTime now = OffsetDateTime.now(clock);
        RefundQuote quote = calculateRefund(booking, now);
        processCancellation(booking, quote, now);
        enqueueCancellation(bookingId, customer.email(), quote);
        return new CancellationView(bookingId, "CANCELLED", quote.amount(), quote.percent(), "INR");
    }

    @Transactional
    public int releaseExpiredHolds() {
        return releaseExpiredHolds(OffsetDateTime.now(clock));
    }

    private LinkedHashSet<Long> validateSeatSelection(List<Long> requestedSeatIds) {
        LinkedHashSet<Long> uniqueSeatIds = new LinkedHashSet<>(requestedSeatIds);
        if (uniqueSeatIds.isEmpty() || uniqueSeatIds.size() > 10) {
            throw ApiException.badRequest("INVALID_SEAT_COUNT", "Select between 1 and 10 unique seats");
        }
        return uniqueSeatIds;
    }

    private Showing requireBookableShowing(long showingId, OffsetDateTime now) {
        Showing showing = showings.findById(showingId)
                .orElseThrow(() -> ApiException.notFound("SHOWING_NOT_FOUND", "Showing not found"));
        if (!"SCHEDULED".equals(showing.getStatus()) || !showing.getStartsAt().isAfter(now)) {
            throw ApiException.conflict("SHOWING_UNAVAILABLE", "The showing is no longer bookable");
        }
        return showing;
    }

    private List<ShowSeat> lockAvailableSeats(long showingId, LinkedHashSet<Long> requestedSeatIds) {
        List<ShowSeat> seats = inventory.findByShowingIdAndIdInOrderByIdAsc(showingId, requestedSeatIds);
        if (seats.size() != requestedSeatIds.size()) {
            throw ApiException.notFound("SEAT_NOT_FOUND", "One or more seats do not belong to this showing");
        }
        List<String> unavailable = seats.stream().filter(seat -> !"AVAILABLE".equals(seat.getStatus()))
                .map(seat -> seat.getSeat().label()).toList();
        if (!unavailable.isEmpty()) {
            throw ApiException.conflict("SEAT_UNAVAILABLE", "Seats are no longer available: " + unavailable);
        }
        return seats;
    }

    private SeatHold buildHold(long customerId, Showing showing, List<ShowSeat> seats, OffsetDateTime now) {
        String holdId = UUID.randomUUID().toString();
        OffsetDateTime expiresAt = now.plus(holdTtl);
        SeatHold hold = new SeatHold(holdId, customerId, showing, expiresAt, now);
        seats.forEach(seat -> {
            hold.addItem(seat);
            seat.hold(holdId, expiresAt);
        });
        return hold;
    }

    private HoldView toHoldView(SeatHold hold, List<ShowSeat> seats) {
        return new HoldView(hold.getId(), hold.getShowing().getId(), labels(seats), subtotal(seats),
                hold.getExpiresAt(), hold.getStatus());
    }

    private Optional<BookingView> findExistingBooking(long customerId, String idempotencyKey) {
        return bookings.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                .map(existing -> booking(customerId, existing.getId()));
    }

    private SeatHold lockActiveHold(String holdId, long customerId, OffsetDateTime now) {
        SeatHold hold = holds.findLockedById(holdId)
                .orElseThrow(() -> ApiException.notFound("HOLD_NOT_FOUND", "Hold not found"));
        if (hold.getCustomerId() != customerId) {
            throw ApiException.notFound("HOLD_NOT_FOUND", "Hold not found");
        }
        if (!"ACTIVE".equals(hold.getStatus()) || !hold.getExpiresAt().isAfter(now)) {
            expireHold(hold);
            throw ApiException.gone("HOLD_EXPIRED", "The seat hold has expired");
        }
        return hold;
    }

    private List<ShowSeat> lockHeldSeats(String holdId) {
        List<ShowSeat> seats = inventory.findByHoldIdOrderByIdAsc(holdId);
        if (seats.isEmpty() || seats.stream().anyMatch(seat -> !"HELD".equals(seat.getStatus()))) {
            throw ApiException.conflict("HOLD_INVALID", "The held inventory is no longer valid");
        }
        return seats;
    }

    private Price calculatePrice(List<ShowSeat> seats, String discountCode, OffsetDateTime now) {
        long subtotal = subtotal(seats);
        DiscountCode discount = discountCode == null || discountCode.isBlank()
                ? null : lockAndValidateDiscount(discountCode, subtotal, now);
        long discountAmount = discount == null ? 0 : discount.discountFor(subtotal);
        return new Price(subtotal, discountAmount, subtotal - discountAmount, discount);
    }

    private PaymentGateway.PaymentReceipt capturePayment(long customerId, String idempotencyKey,
                                                          String paymentMethod, Price price) {
        return payments.capture(price.total(), "INR", paymentMethod, customerId + ":" + idempotencyKey);
    }

    private Booking buildBooking(long customerId, String idempotencyKey, SeatHold hold,
                                 List<ShowSeat> seats, Price price, OffsetDateTime now) {
        Booking booking = new Booking(UUID.randomUUID().toString(), customerId, hold.getShowing(), hold,
                idempotencyKey, price.subtotal(), price.discountAmount(), price.discountCode(), now);
        seats.forEach(booking::addItem);
        return booking;
    }

    private void completeBooking(Booking booking, SeatHold hold, List<ShowSeat> seats,
                                 DiscountCode discount, PaymentGateway.PaymentReceipt receipt,
                                 OffsetDateTime now) {
        seats.forEach(seat -> seat.book(booking.getId()));
        hold.convert();
        if (discount != null) discount.redeem();
        bookings.save(booking);
        paymentRecords.save(new Payment(UUID.randomUUID().toString(), booking, booking.getTotalCents(),
                receipt.reference(), now));
    }

    private void enqueueConfirmation(Booking booking, String email, List<ShowSeat> seats) {
        outbox.enqueue(booking.getId(), "BOOKING_CONFIRMED",
                Map.of("bookingId", booking.getId(), "email", email,
                        "seatLabels", labels(seats), "totalCents", booking.getTotalCents()),
                "booking-confirmed:" + booking.getId());
    }

    private Booking lockCancellableBooking(String bookingId, long customerId) {
        Booking booking = bookings.findLockedById(bookingId)
                .orElseThrow(() -> ApiException.notFound("BOOKING_NOT_FOUND", "Booking not found"));
        if (booking.getCustomerId() != customerId) {
            throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking not found");
        }
        if (!"CONFIRMED".equals(booking.getStatus())) {
            throw ApiException.conflict("BOOKING_ALREADY_CANCELLED", "Booking is already cancelled");
        }
        return booking;
    }

    private RefundQuote calculateRefund(Booking booking, OffsetDateTime now) {
        long minutesBefore = Math.max(0, Duration.between(now, booking.getShowing().getStartsAt()).toMinutes());
        int refundPercent = refundRules
                .findFirstByPolicyIdAndMinimumMinutesBeforeLessThanEqualOrderByMinimumMinutesBeforeDesc(
                        booking.getShowing().getRefundPolicy().getId(), minutesBefore)
                .map(rule -> rule.getRefundPercent()).orElse(0);
        long refundAmount = Math.multiplyExact(booking.getTotalCents(), refundPercent) / 100;
        return new RefundQuote(refundAmount, refundPercent);
    }

    private void processCancellation(Booking booking, RefundQuote quote, OffsetDateTime now) {
        Payment payment = paymentRecords.findByBookingId(booking.getId());
        String refundReference = quote.amount() == 0 ? null : payments.refund(payment.getProviderReference(),
                quote.amount(), "cancel:" + booking.getId());
        booking.cancel(now);
        inventory.findByBookingIdOrderByIdAsc(booking.getId()).forEach(ShowSeat::release);
        refunds.save(new Refund(UUID.randomUUID().toString(), booking, quote.amount(),
                quote.percent(), refundReference, now));
        if (quote.amount() > 0) payment.markRefunded();
    }

    private void enqueueCancellation(String bookingId, String email, RefundQuote quote) {
        outbox.enqueue(bookingId, "BOOKING_CANCELLED",
                Map.of("bookingId", bookingId, "email", email,
                        "refundCents", quote.amount(), "refundPercent", quote.percent()),
                "booking-cancelled:" + bookingId);
    }

    private DiscountCode lockAndValidateDiscount(String requestedCode, long subtotal, OffsetDateTime now) {
        DiscountCode discount = discounts.findLockedByCode(requestedCode.toUpperCase())
                .orElseThrow(() -> ApiException.badRequest("DISCOUNT_INVALID", "Discount code is invalid"));
        if (!discount.isApplicable(subtotal, now)) {
            throw ApiException.badRequest("DISCOUNT_NOT_APPLICABLE", "Discount code is not applicable");
        }
        return discount;
    }

    private void expireHold(SeatHold hold) {
        hold.expire();
        inventory.findByHoldIdOrderByIdAsc(hold.getId()).forEach(ShowSeat::release);
    }

    private int releaseExpiredHolds(OffsetDateTime now) {
        holds.findByStatusAndExpiresAtLessThanEqual("ACTIVE", now).forEach(SeatHold::expire);
        List<ShowSeat> expiredSeats = inventory
                .findByStatusAndHoldExpiresAtLessThanEqualOrderByIdAsc("HELD", now);
        expiredSeats.forEach(ShowSeat::release);
        return expiredSeats.size();
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

    private record Price(long subtotal, long discountAmount, long total, DiscountCode discount) {
        private String discountCode() {
            return discount == null ? null : discount.getCode();
        }
    }

    private record RefundQuote(long amount, int percent) { }

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
