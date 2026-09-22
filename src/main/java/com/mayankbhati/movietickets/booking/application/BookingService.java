package com.mayankbhati.movietickets.booking.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mayankbhati.movietickets.identity.domain.Actor;
import com.mayankbhati.movietickets.notification.application.OutboxService;
import com.mayankbhati.movietickets.shared.ApiException;

@Service
public class BookingService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PaymentGateway payments;
    private final OutboxService outbox;
    private final Clock clock;
    private final Duration holdTtl;

    public BookingService(NamedParameterJdbcTemplate jdbc, PaymentGateway payments,
                          OutboxService outbox, Clock clock,
                          @Value("${booking.hold-ttl:PT5M}") Duration holdTtl) {
        this.jdbc = jdbc;
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
        releaseExpiredHolds(now);
        ShowingState showing = loadShowing(showingId);
        if (!"SCHEDULED".equals(showing.status()) || !showing.startsAt().isAfter(now)) {
            throw ApiException.conflict("SHOWING_UNAVAILABLE", "The showing is no longer bookable");
        }

        List<LockedSeat> seats = jdbc.query("""
                SELECT ss.id, ss.status, ss.price_cents, s.row_label, s.seat_number
                FROM show_seat ss JOIN seat s ON s.id = ss.seat_id
                WHERE ss.showing_id = :showingId AND ss.id IN (:seatIds)
                ORDER BY ss.id FOR UPDATE
                """, new MapSqlParameterSource().addValue("showingId", showingId)
                .addValue("seatIds", uniqueSeatIds), (rs, rowNum) -> new LockedSeat(
                rs.getLong("id"), rs.getString("status"), rs.getLong("price_cents"),
                rs.getString("row_label") + rs.getInt("seat_number")));
        if (seats.size() != uniqueSeatIds.size()) {
            throw ApiException.notFound("SEAT_NOT_FOUND", "One or more seats do not belong to this showing");
        }
        List<String> unavailable = seats.stream().filter(seat -> !"AVAILABLE".equals(seat.status()))
                .map(LockedSeat::label).toList();
        if (!unavailable.isEmpty()) {
            throw ApiException.conflict("SEAT_UNAVAILABLE", "Seats are no longer available: " + unavailable);
        }

        String holdId = UUID.randomUUID().toString();
        OffsetDateTime expiresAt = now.plus(holdTtl);
        jdbc.update("""
                INSERT INTO seat_hold(id, customer_id, showing_id, status, expires_at, created_at)
                VALUES (:id, :customerId, :showingId, 'ACTIVE', :expiresAt, :createdAt)
                """, Map.of("id", holdId, "customerId", customer.id(), "showingId", showingId,
                "expiresAt", expiresAt, "createdAt", now));

        List<MapSqlParameterSource> items = seats.stream().map(seat -> new MapSqlParameterSource()
                .addValue("holdId", holdId).addValue("showSeatId", seat.id())
                .addValue("price", seat.priceCents())).toList();
        jdbc.batchUpdate("""
                INSERT INTO seat_hold_item(hold_id, show_seat_id, price_cents)
                VALUES (:holdId, :showSeatId, :price)
                """, items.toArray(MapSqlParameterSource[]::new));
        jdbc.update("""
                UPDATE show_seat
                SET status = 'HELD', hold_id = :holdId, hold_expires_at = :expiresAt,
                    version = version + 1
                WHERE id IN (:seatIds) AND status = 'AVAILABLE'
                """, new MapSqlParameterSource().addValue("holdId", holdId)
                .addValue("expiresAt", expiresAt).addValue("seatIds", uniqueSeatIds));
        return new HoldView(holdId, showingId, seats.stream().map(LockedSeat::label).toList(),
                seats.stream().mapToLong(LockedSeat::priceCents).sum(), expiresAt, "ACTIVE");
    }

    @Transactional
    public BookingView confirm(Actor customer, String holdId, String discountCode,
                               String paymentMethod, String idempotencyKey) {
        BookingView existing = findByIdempotencyKey(customer.id(), idempotencyKey);
        if (existing != null) {
            return existing;
        }
        HoldState hold = lockHold(holdId);
        if (hold.customerId() != customer.id()) {
            throw ApiException.notFound("HOLD_NOT_FOUND", "Hold not found");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!"ACTIVE".equals(hold.status()) || !hold.expiresAt().isAfter(now)) {
            expireHold(holdId);
            throw ApiException.gone("HOLD_EXPIRED", "The seat hold has expired");
        }
        List<LockedSeat> seats = lockSeatsForHold(holdId);
        if (seats.isEmpty() || seats.stream().anyMatch(seat -> !"HELD".equals(seat.status()))) {
            throw ApiException.conflict("HOLD_INVALID", "The held inventory is no longer valid");
        }

        long subtotal = seats.stream().mapToLong(LockedSeat::priceCents).sum();
        Discount discount = discountCode == null || discountCode.isBlank()
                ? Discount.none() : lockAndValidateDiscount(discountCode, subtotal, now);
        long total = subtotal - discount.amountCents();
        PaymentGateway.PaymentReceipt receipt = payments.capture(total, "INR", paymentMethod,
                customer.id() + ":" + idempotencyKey);
        String bookingId = UUID.randomUUID().toString();

        jdbc.update("""
                INSERT INTO booking(id, customer_id, showing_id, hold_id, idempotency_key, status,
                                    subtotal_cents, discount_cents, total_cents, currency,
                                    discount_code, created_at)
                VALUES (:id, :customerId, :showingId, :holdId, :idempotencyKey, 'CONFIRMED',
                        :subtotal, :discount, :total, 'INR', :discountCode, :createdAt)
                """, new MapSqlParameterSource().addValue("id", bookingId)
                .addValue("customerId", customer.id()).addValue("showingId", hold.showingId())
                .addValue("holdId", holdId).addValue("idempotencyKey", idempotencyKey)
                .addValue("subtotal", subtotal).addValue("discount", discount.amountCents())
                .addValue("total", total).addValue("discountCode", discount.code())
                .addValue("createdAt", now));

        List<MapSqlParameterSource> bookingItems = seats.stream().map(seat -> new MapSqlParameterSource()
                .addValue("bookingId", bookingId).addValue("showSeatId", seat.id())
                .addValue("label", seat.label()).addValue("price", seat.priceCents())).toList();
        jdbc.batchUpdate("""
                INSERT INTO booking_item(booking_id, show_seat_id, seat_label, price_cents)
                VALUES (:bookingId, :showSeatId, :label, :price)
                """, bookingItems.toArray(MapSqlParameterSource[]::new));
        jdbc.update("""
                INSERT INTO payment(id, booking_id, amount_cents, status, provider_reference, created_at)
                VALUES (:id, :bookingId, :amount, 'CAPTURED', :reference, :createdAt)
                """, Map.of("id", UUID.randomUUID().toString(), "bookingId", bookingId,
                "amount", total, "reference", receipt.reference(), "createdAt", now));
        jdbc.update("""
                UPDATE show_seat SET status = 'BOOKED', booking_id = :bookingId,
                    hold_id = NULL, hold_expires_at = NULL, version = version + 1
                WHERE hold_id = :holdId AND status = 'HELD'
                """, Map.of("bookingId", bookingId, "holdId", holdId));
        jdbc.update("UPDATE seat_hold SET status = 'CONVERTED' WHERE id = :id", Map.of("id", holdId));
        if (discount.code() != null) {
            jdbc.update("""
                    UPDATE discount_code SET redemption_count = redemption_count + 1 WHERE code = :code
                    """, Map.of("code", discount.code()));
        }
        outbox.enqueue(bookingId, "BOOKING_CONFIRMED",
                Map.of("bookingId", bookingId, "email", customer.email(), "seatLabels",
                        seats.stream().map(LockedSeat::label).toList(), "totalCents", total),
                "booking-confirmed:" + bookingId);
        return booking(customer.id(), bookingId);
    }

    public List<BookingSummary> history(Actor customer) {
        return jdbc.query("""
                SELECT b.id, b.status, m.title, sh.starts_at, b.total_cents, b.currency, b.created_at
                FROM booking b
                JOIN showing sh ON sh.id = b.showing_id
                JOIN movie m ON m.id = sh.movie_id
                WHERE b.customer_id = :customerId
                ORDER BY b.created_at DESC
                """, Map.of("customerId", customer.id()), (rs, rowNum) -> new BookingSummary(
                rs.getString("id"), rs.getString("status"), rs.getString("title"),
                rs.getObject("starts_at", OffsetDateTime.class), rs.getLong("total_cents"),
                rs.getString("currency"), rs.getObject("created_at", OffsetDateTime.class)));
    }

    public BookingView booking(long customerId, String bookingId) {
        List<BookingView> results = jdbc.query("""
                SELECT b.id, b.status, b.showing_id, m.title, sh.starts_at, b.subtotal_cents,
                       b.discount_cents, b.total_cents, b.currency, b.discount_code,
                       b.created_at, b.cancelled_at
                FROM booking b
                JOIN showing sh ON sh.id = b.showing_id
                JOIN movie m ON m.id = sh.movie_id
                WHERE b.id = :bookingId AND b.customer_id = :customerId
                """, Map.of("bookingId", bookingId, "customerId", customerId), (rs, rowNum) -> {
            List<String> labels = jdbc.query("""
                    SELECT seat_label FROM booking_item WHERE booking_id = :bookingId ORDER BY seat_label
                    """, Map.of("bookingId", bookingId), (itemRs, itemRow) -> itemRs.getString(1));
            return new BookingView(rs.getString("id"), rs.getString("status"),
                    rs.getLong("showing_id"), rs.getString("title"),
                    rs.getObject("starts_at", OffsetDateTime.class), labels,
                    rs.getLong("subtotal_cents"), rs.getLong("discount_cents"),
                    rs.getLong("total_cents"), rs.getString("currency"),
                    rs.getString("discount_code"), rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("cancelled_at", OffsetDateTime.class));
        });
        return results.stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("BOOKING_NOT_FOUND", "Booking not found"));
    }

    @Transactional
    public CancellationView cancel(Actor customer, String bookingId) {
        List<CancellationState> states = jdbc.query("""
                SELECT b.id, b.customer_id, b.status, b.total_cents, sh.starts_at,
                       sh.refund_policy_id, p.provider_reference
                FROM booking b
                JOIN showing sh ON sh.id = b.showing_id
                JOIN payment p ON p.booking_id = b.id
                WHERE b.id = :bookingId FOR UPDATE
                """, Map.of("bookingId", bookingId), (rs, rowNum) -> new CancellationState(
                rs.getString("id"), rs.getLong("customer_id"), rs.getString("status"),
                rs.getLong("total_cents"), rs.getObject("starts_at", OffsetDateTime.class),
                rs.getLong("refund_policy_id"), rs.getString("provider_reference")));
        CancellationState state = states.stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("BOOKING_NOT_FOUND", "Booking not found"));
        if (state.customerId() != customer.id()) {
            throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking not found");
        }
        if (!"CONFIRMED".equals(state.status())) {
            throw ApiException.conflict("BOOKING_ALREADY_CANCELLED", "Booking is already cancelled");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        long minutesBefore = Math.max(0, Duration.between(now, state.startsAt()).toMinutes());
        int refundPercent = jdbc.query("""
                SELECT refund_percent FROM refund_rule
                WHERE policy_id = :policyId AND minimum_minutes_before <= :minutesBefore
                ORDER BY minimum_minutes_before DESC LIMIT 1
                """, Map.of("policyId", state.refundPolicyId(), "minutesBefore", minutesBefore),
                (rs, rowNum) -> rs.getInt(1)).stream().findFirst().orElse(0);
        long refundAmount = Math.multiplyExact(state.totalCents(), refundPercent) / 100;
        String refundReference = refundAmount == 0 ? null : payments.refund(state.paymentReference(),
                refundAmount, "cancel:" + bookingId);

        jdbc.update("""
                UPDATE booking SET status = 'CANCELLED', cancelled_at = :now WHERE id = :bookingId
                """, Map.of("now", now, "bookingId", bookingId));
        jdbc.update("""
                UPDATE show_seat SET status = 'AVAILABLE', booking_id = NULL, version = version + 1
                WHERE booking_id = :bookingId AND status = 'BOOKED'
                """, Map.of("bookingId", bookingId));
        jdbc.update("""
                INSERT INTO refund(id, booking_id, amount_cents, refund_percent, provider_reference, created_at)
                VALUES (:id, :bookingId, :amount, :percent, :reference, :createdAt)
                """, new MapSqlParameterSource().addValue("id", UUID.randomUUID().toString())
                .addValue("bookingId", bookingId).addValue("amount", refundAmount)
                .addValue("percent", refundPercent).addValue("reference", refundReference)
                .addValue("createdAt", now));
        if (refundAmount > 0) {
            jdbc.update("UPDATE payment SET status = 'REFUNDED' WHERE booking_id = :bookingId",
                    Map.of("bookingId", bookingId));
        }
        outbox.enqueue(bookingId, "BOOKING_CANCELLED",
                Map.of("bookingId", bookingId, "email", customer.email(),
                        "refundCents", refundAmount, "refundPercent", refundPercent),
                "booking-cancelled:" + bookingId);
        return new CancellationView(bookingId, "CANCELLED", refundAmount, refundPercent, "INR");
    }

    @Transactional
    public int releaseExpiredHolds() {
        return releaseExpiredHolds(OffsetDateTime.now(clock));
    }

    private int releaseExpiredHolds(OffsetDateTime now) {
        jdbc.update("""
                UPDATE seat_hold SET status = 'EXPIRED'
                WHERE status = 'ACTIVE' AND expires_at <= :now
                """, Map.of("now", now));
        return jdbc.update("""
                UPDATE show_seat SET status = 'AVAILABLE', hold_id = NULL,
                    hold_expires_at = NULL, version = version + 1
                WHERE status = 'HELD' AND hold_expires_at <= :now
                """, Map.of("now", now));
    }

    private ShowingState loadShowing(long showingId) {
        return jdbc.query("SELECT starts_at, status FROM showing WHERE id = :id",
                Map.of("id", showingId), (rs, rowNum) -> new ShowingState(
                        rs.getObject("starts_at", OffsetDateTime.class), rs.getString("status")))
                .stream().findFirst().orElseThrow(() ->
                        ApiException.notFound("SHOWING_NOT_FOUND", "Showing not found"));
    }

    private HoldState lockHold(String holdId) {
        return jdbc.query("""
                SELECT customer_id, showing_id, status, expires_at FROM seat_hold
                WHERE id = :id FOR UPDATE
                """, Map.of("id", holdId), (rs, rowNum) -> new HoldState(
                rs.getLong("customer_id"), rs.getLong("showing_id"), rs.getString("status"),
                rs.getObject("expires_at", OffsetDateTime.class))).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("HOLD_NOT_FOUND", "Hold not found"));
    }

    private List<LockedSeat> lockSeatsForHold(String holdId) {
        return jdbc.query("""
                SELECT ss.id, ss.status, ss.price_cents, s.row_label, s.seat_number
                FROM show_seat ss JOIN seat s ON s.id = ss.seat_id
                WHERE ss.hold_id = :holdId ORDER BY ss.id FOR UPDATE
                """, Map.of("holdId", holdId), (rs, rowNum) -> new LockedSeat(
                rs.getLong("id"), rs.getString("status"), rs.getLong("price_cents"),
                rs.getString("row_label") + rs.getInt("seat_number")));
    }

    private Discount lockAndValidateDiscount(String requestedCode, long subtotal, OffsetDateTime now) {
        String code = requestedCode.toUpperCase();
        List<DiscountState> discounts = jdbc.query("""
                SELECT kind, value_amount, minimum_order_cents, valid_from, valid_until,
                       max_redemptions, redemption_count, active
                FROM discount_code WHERE code = :code FOR UPDATE
                """, Map.of("code", code), (rs, rowNum) -> new DiscountState(
                rs.getString("kind"), rs.getLong("value_amount"), rs.getLong("minimum_order_cents"),
                rs.getObject("valid_from", OffsetDateTime.class),
                rs.getObject("valid_until", OffsetDateTime.class),
                (Integer) rs.getObject("max_redemptions"), rs.getInt("redemption_count"),
                rs.getBoolean("active")));
        DiscountState state = discounts.stream().findFirst()
                .orElseThrow(() -> ApiException.badRequest("DISCOUNT_INVALID", "Discount code is invalid"));
        if (!state.active() || now.isBefore(state.validFrom()) || !now.isBefore(state.validUntil())
                || subtotal < state.minimumOrderCents()
                || state.maxRedemptions() != null && state.redemptionCount() >= state.maxRedemptions()) {
            throw ApiException.badRequest("DISCOUNT_NOT_APPLICABLE", "Discount code is not applicable");
        }
        long amount = "PERCENT".equals(state.kind())
                ? Math.multiplyExact(subtotal, state.valueAmount()) / 100
                : state.valueAmount();
        return new Discount(code, Math.min(subtotal, amount));
    }

    private void expireHold(String holdId) {
        jdbc.update("UPDATE seat_hold SET status = 'EXPIRED' WHERE id = :id AND status = 'ACTIVE'",
                Map.of("id", holdId));
        jdbc.update("""
                UPDATE show_seat SET status = 'AVAILABLE', hold_id = NULL,
                    hold_expires_at = NULL, version = version + 1
                WHERE hold_id = :id AND status = 'HELD'
                """, Map.of("id", holdId));
    }

    private BookingView findByIdempotencyKey(long customerId, String idempotencyKey) {
        List<String> ids = jdbc.query("""
                SELECT id FROM booking WHERE customer_id = :customerId AND idempotency_key = :key
                """, Map.of("customerId", customerId, "key", idempotencyKey),
                (rs, rowNum) -> rs.getString(1));
        return ids.isEmpty() ? null : booking(customerId, ids.getFirst());
    }

    public record HoldView(String holdId, long showingId, List<String> seatLabels,
                           long subtotalCents, OffsetDateTime expiresAt, String status) {
    }

    public record BookingView(String id, String status, long showingId, String movieTitle,
                              OffsetDateTime startsAt, List<String> seatLabels, long subtotalCents,
                              long discountCents, long totalCents, String currency, String discountCode,
                              OffsetDateTime createdAt, OffsetDateTime cancelledAt) {
    }

    public record BookingSummary(String id, String status, String movieTitle, OffsetDateTime startsAt,
                                 long totalCents, String currency, OffsetDateTime createdAt) {
    }

    public record CancellationView(String bookingId, String status, long refundCents,
                                   int refundPercent, String currency) {
    }

    private record ShowingState(OffsetDateTime startsAt, String status) {
    }

    private record LockedSeat(long id, String status, long priceCents, String label) {
    }

    private record HoldState(long customerId, long showingId, String status, OffsetDateTime expiresAt) {
    }

    private record Discount(String code, long amountCents) {
        static Discount none() {
            return new Discount(null, 0);
        }
    }

    private record DiscountState(String kind, long valueAmount, long minimumOrderCents,
                                 OffsetDateTime validFrom, OffsetDateTime validUntil,
                                 Integer maxRedemptions, int redemptionCount, boolean active) {
    }

    private record CancellationState(String id, long customerId, String status, long totalCents,
                                     OffsetDateTime startsAt, long refundPolicyId,
                                     String paymentReference) {
    }
}
