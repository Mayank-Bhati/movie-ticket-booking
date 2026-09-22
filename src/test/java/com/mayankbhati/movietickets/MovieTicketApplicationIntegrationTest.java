package com.mayankbhati.movietickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.mayankbhati.movietickets.booking.application.BookingService;
import com.mayankbhati.movietickets.booking.application.BookingService.BookingView;
import com.mayankbhati.movietickets.booking.application.BookingService.CancellationView;
import com.mayankbhati.movietickets.booking.application.BookingService.HoldView;
import com.mayankbhati.movietickets.catalog.application.CatalogService;
import com.mayankbhati.movietickets.catalog.application.CatalogService.RefundRuleInput;
import com.mayankbhati.movietickets.catalog.application.CatalogService.SeatView;
import com.mayankbhati.movietickets.identity.domain.Actor;
import com.mayankbhati.movietickets.identity.domain.Actor.Role;
import com.mayankbhati.movietickets.identity.infrastructure.UserAccountStore;
import com.mayankbhati.movietickets.shared.ApiException;

@SpringBootTest
@AutoConfigureMockMvc
class MovieTicketApplicationIntegrationTest {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Autowired
    CatalogService catalog;

    @Autowired
    BookingService bookings;

    @Autowired
    UserAccountStore users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    Clock clock;

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void cleanDatabase() {
        for (String table : List.of("outbox_event", "refund", "payment", "booking_item", "booking",
                "seat_hold_item", "seat_hold", "show_seat", "discount_code", "showing",
                "refund_rule", "refund_policy", "seat", "screen", "theater", "city", "movie",
                "app_user")) {
            jdbc.getJdbcTemplate().update("DELETE FROM " + table);
        }
        jdbc.update("UPDATE pricing_tier SET multiplier_bps = 10000 WHERE category = 'REGULAR'", Map.of());
        jdbc.update("UPDATE pricing_tier SET multiplier_bps = 15000 WHERE category = 'PREMIUM'", Map.of());
    }

    @AfterAll
    static void shutdownExecutor() {
        EXECUTOR.shutdownNow();
    }

    @Test
    void buildsSeatInventoryWithTierAndWeekendPricing() {
        Fixture fixture = fixture(nextSaturday(), 12500);

        List<SeatView> seats = catalog.seats(fixture.showingId());

        assertThat(seats).hasSize(4);
        assertThat(seats).filteredOn(seat -> seat.category().equals("REGULAR"))
                .extracting(SeatView::priceCents).containsOnly(1250L);
        assertThat(seats).filteredOn(seat -> seat.category().equals("PREMIUM"))
                .extracting(SeatView::priceCents).containsOnly(1875L);
    }

    @Test
    void rejectsOverlappingShowingsOnTheSameScreen() {
        Fixture fixture = fixture(clock.instant().plus(Duration.ofDays(3)), 12500);

        assertThatThrownBy(() -> catalog.createShowing(fixture.movieId(), fixture.screenId(),
                fixture.policyId(), fixture.startsAt().plus(Duration.ofMinutes(30)), 1000, 12500))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("SCREEN_SCHEDULE_CONFLICT"));
    }

    @Test
    void holdsConfirmsAndReturnsAnIdempotentBooking() {
        Fixture fixture = fixture(clock.instant().plus(Duration.ofDays(3)), 12500);
        Actor customer = customer("one@example.com");
        long seatId = catalog.seats(fixture.showingId()).getFirst().showSeatId();
        HoldView hold = bookings.createHold(customer, fixture.showingId(), List.of(seatId));

        BookingView first = bookings.confirm(customer, hold.holdId(), null, "mock-card", "request-1");
        BookingView retry = bookings.confirm(customer, hold.holdId(), null, "mock-card", "request-1");

        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(first.status()).isEqualTo("CONFIRMED");
        assertThat(catalog.seats(fixture.showingId()).getFirst().status()).isEqualTo("BOOKED");
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    @Test
    void rollsBackADeclinedPaymentAndAllowsRetry() {
        Fixture fixture = fixture(clock.instant().plus(Duration.ofDays(3)), 12500);
        Actor customer = customer("retry@example.com");
        long seatId = catalog.seats(fixture.showingId()).getFirst().showSeatId();
        HoldView hold = bookings.createHold(customer, fixture.showingId(), List.of(seatId));

        assertThatThrownBy(() -> bookings.confirm(customer, hold.holdId(), null, "decline", "declined"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("PAYMENT_DECLINED"));

        BookingView retry = bookings.confirm(customer, hold.holdId(), null, "mock-card", "approved");
        assertThat(retry.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void appliesDiscountAndRefundPolicyOnCancellation() {
        Fixture fixture = fixture(clock.instant().plus(Duration.ofDays(3)), 12500);
        Actor customer = customer("refund@example.com");
        catalog.createDiscount("SAVE10", "PERCENT", 10, 0, clock.instant().minusSeconds(60),
                clock.instant().plus(Duration.ofDays(1)), 5);
        long seatId = catalog.seats(fixture.showingId()).getFirst().showSeatId();
        HoldView hold = bookings.createHold(customer, fixture.showingId(), List.of(seatId));
        BookingView booking = bookings.confirm(customer, hold.holdId(), "save10", "mock-card", "discounted");

        CancellationView cancellation = bookings.cancel(customer, booking.id());

        assertThat(booking.discountCents()).isEqualTo(100);
        assertThat(cancellation.refundPercent()).isEqualTo(100);
        assertThat(cancellation.refundCents()).isEqualTo(900);
        assertThat(catalog.seats(fixture.showingId()).getFirst().status()).isEqualTo("AVAILABLE");
        assertThat(count("outbox_event")).isEqualTo(2);
    }

    @Test
    void releasesExpiredHoldsForAnotherCustomer() {
        Fixture fixture = fixture(clock.instant().plus(Duration.ofDays(3)), 12500);
        Actor firstCustomer = customer("first@example.com");
        Actor secondCustomer = customer("second@example.com");
        long seatId = catalog.seats(fixture.showingId()).getFirst().showSeatId();
        HoldView hold = bookings.createHold(firstCustomer, fixture.showingId(), List.of(seatId));
        jdbc.update("UPDATE seat_hold SET expires_at = :past WHERE id = :id",
                Map.of("past", clock.instant().minusSeconds(30), "id", hold.holdId()));
        jdbc.update("UPDATE show_seat SET hold_expires_at = :past WHERE hold_id = :id",
                Map.of("past", clock.instant().minusSeconds(30), "id", hold.holdId()));

        bookings.releaseExpiredHolds();
        HoldView replacement = bookings.createHold(secondCustomer, fixture.showingId(), List.of(seatId));

        assertThat(replacement.status()).isEqualTo("ACTIVE");
    }

    @Test
    void serializesConcurrentAttemptsForTheSameSeat() throws Exception {
        Fixture fixture = fixture(clock.instant().plus(Duration.ofDays(3)), 12500);
        Actor firstCustomer = customer("race-one@example.com");
        Actor secondCustomer = customer("race-two@example.com");
        long seatId = catalog.seats(fixture.showingId()).getFirst().showSeatId();
        CountDownLatch start = new CountDownLatch(1);

        Future<Object> first = EXECUTOR.submit(() -> attemptHold(start, firstCustomer, fixture.showingId(), seatId));
        Future<Object> second = EXECUTOR.submit(() -> attemptHold(start, secondCustomer, fixture.showingId(), seatId));
        start.countDown();
        List<Object> outcomes = List.of(first.get(), second.get());

        assertThat(outcomes).filteredOn(HoldView.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(ApiException.class::isInstance).hasSize(1);
        ApiException failure = (ApiException) outcomes.stream().filter(ApiException.class::isInstance)
                .findFirst().orElseThrow();
        assertThat(failure.code()).isEqualTo("SEAT_UNAVAILABLE");
    }

    @Test
    void enforcesPublicCustomerAndAdminHttpBoundaries() throws Exception {
        users.createAdminIfMissing("admin@example.com", passwordEncoder.encode("admin-password"));
        users.createCustomer("web@example.com", passwordEncoder.encode("customer-password"));

        mockMvc.perform(get("/api/v1/cities"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/cities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Delhi\",\"timezone\":\"Asia/Kolkata\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/cities")
                        .with(httpBasic("web@example.com", "customer-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Delhi\",\"timezone\":\"Asia/Kolkata\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/cities")
                        .with(httpBasic("admin@example.com", "admin-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Delhi\",\"timezone\":\"Asia/Kolkata\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void validatesCustomerRegistration() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"password\":\"good-password\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@example.com"));
    }

    private Object attemptHold(CountDownLatch start, Actor actor, long showingId, long seatId)
            throws InterruptedException {
        start.await();
        try {
            return bookings.createHold(actor, showingId, List.of(seatId));
        } catch (ApiException exception) {
            return exception;
        }
    }

    private Fixture fixture(Instant startsAt, int weekendMultiplierBps) {
        long cityId = catalog.createCity("Bengaluru", "Asia/Kolkata");
        long theaterId = catalog.createTheater(cityId, "Central Cinema", "1 Cinema Road");
        CatalogService.ScreenCreated screen = catalog.createScreen(theaterId, "Screen 1", 2, 2, Set.of(2));
        long movieId = catalog.createMovie("A Test Movie", 120, "U/A", "English");
        long policyId = catalog.createRefundPolicy("Standard", List.of(
                new RefundRuleInput(0, 0),
                new RefundRuleInput(120, 50),
                new RefundRuleInput(1440, 100)));
        long showingId = catalog.createShowing(movieId, screen.id(), policyId, startsAt, 1000,
                weekendMultiplierBps);
        return new Fixture(showingId, movieId, screen.id(), policyId, startsAt);
    }

    private Actor customer(String email) {
        long id = users.createCustomer(email, passwordEncoder.encode("customer-password"));
        return new Actor(id, email, Role.CUSTOMER);
    }

    private long count(String table) {
        return jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private Instant nextSaturday() {
        ZonedDateTime date = clock.instant().atZone(ZoneOffset.UTC).plusDays(2).withHour(12)
                .withMinute(0).withSecond(0).withNano(0);
        while (date.getDayOfWeek() != DayOfWeek.SATURDAY) {
            date = date.plusDays(1);
        }
        return date.toInstant();
    }

    private record Fixture(long showingId, long movieId, long screenId, long policyId,
                           Instant startsAt) {
    }
}

