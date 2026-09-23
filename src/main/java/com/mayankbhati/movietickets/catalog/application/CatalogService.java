package com.mayankbhati.movietickets.catalog.application;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mayankbhati.movietickets.catalog.domain.City;
import com.mayankbhati.movietickets.catalog.domain.DiscountCode;
import com.mayankbhati.movietickets.catalog.domain.Movie;
import com.mayankbhati.movietickets.catalog.domain.PricingTier;
import com.mayankbhati.movietickets.catalog.domain.RefundPolicy;
import com.mayankbhati.movietickets.catalog.domain.Screen;
import com.mayankbhati.movietickets.catalog.domain.Seat;
import com.mayankbhati.movietickets.catalog.domain.ShowSeat;
import com.mayankbhati.movietickets.catalog.domain.Showing;
import com.mayankbhati.movietickets.catalog.domain.Theater;
import com.mayankbhati.movietickets.catalog.infrastructure.CatalogStore;
import com.mayankbhati.movietickets.shared.ApiException;

@Service
public class CatalogService {
    private final CatalogStore store;
    private final Clock clock;

    public CatalogService(CatalogStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CityView> cities() {
        return store.cities().stream().map(c -> new CityView(c.getId(), c.getName(), c.getTimezone())).toList();
    }

    @Transactional(readOnly = true)
    public List<MovieView> movies() {
        return store.movies().stream().map(m -> new MovieView(m.getId(), m.getTitle(),
                m.getDurationMinutes(), m.getCertificate(), m.getLanguage())).toList();
    }

    @Transactional(readOnly = true)
    public List<ShowingView> showings(Long cityId, Long movieId) {
        return store.showings(cityId, movieId, OffsetDateTime.now(clock)).stream()
                .map(s -> new ShowingView(s.id(), s.movieId(), s.movieTitle(), s.cityId(),
                        s.cityName(), s.theaterName(), s.screenName(), s.startsAt(), s.priceFromCents()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SeatView> seats(long showingId) {
        require(store.showing(showingId), "SHOWING_NOT_FOUND");
        OffsetDateTime now = OffsetDateTime.now(clock);
        return store.seats(showingId).stream().map(ss -> new SeatView(ss.getId(), ss.getSeat().label(),
                ss.getCategory(), ss.getPriceCents(), ss.visibleStatus(now))).toList();
    }

    @Transactional
    public long createCity(String name, String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (Exception exception) {
            throw ApiException.badRequest("INVALID_TIMEZONE", "Timezone must be a valid IANA zone");
        }
        return store.persist(new City(name.trim(), timezone)).getId();
    }

    @Transactional
    public long createTheater(long cityId, String name, String address) {
        City city = require(store.city(cityId), "CITY_NOT_FOUND");
        return store.persist(new Theater(city, name.trim(), address.trim())).getId();
    }

    @Transactional
    public ScreenCreated createScreen(long theaterId, String name, int rows, int seatsPerRow,
                                      Set<Integer> premiumRows) {
        Theater theater = require(store.theater(theaterId), "THEATER_NOT_FOUND");
        if (rows > 26) {
            throw ApiException.badRequest("TOO_MANY_ROWS", "A screen supports at most 26 rows");
        }
        if (premiumRows.stream().anyMatch(row -> row < 1 || row > rows)) {
            throw ApiException.badRequest("INVALID_PREMIUM_ROW", "Premium rows must exist in the layout");
        }
        Screen screen = store.persist(new Screen(theater, name.trim()));
        List<Seat> seats = new ArrayList<>(rows * seatsPerRow);
        for (int row = 1; row <= rows; row++) {
            String rowLabel = String.valueOf((char) ('A' + row - 1));
            String category = premiumRows.contains(row) ? "PREMIUM" : "REGULAR";
            for (int seatNumber = 1; seatNumber <= seatsPerRow; seatNumber++) {
                seats.add(new Seat(screen, rowLabel, seatNumber, category));
            }
        }
        store.persistAll(seats);
        return new ScreenCreated(screen.getId(), seats.size());
    }

    @Transactional
    public long createMovie(String title, int durationMinutes, String certificate, String language) {
        return store.persist(new Movie(title.trim(), durationMinutes, certificate, language.trim())).getId();
    }

    @Transactional
    public void updatePricingTier(String category, int multiplierBps) {
        PricingTier tier = store.pricingTier(category.toUpperCase());
        if (tier == null) {
            throw ApiException.badRequest("INVALID_SEAT_CATEGORY", "Category must be REGULAR or PREMIUM");
        }
        tier.changeMultiplier(multiplierBps);
    }

    @Transactional
    public long createRefundPolicy(String name, List<RefundRuleInput> rules) {
        Set<Long> thresholds = new HashSet<>();
        if (rules.isEmpty() || rules.stream().anyMatch(rule -> !thresholds.add(rule.minimumMinutesBefore()))) {
            throw ApiException.badRequest("INVALID_REFUND_RULES",
                    "Provide at least one rule with unique minute thresholds");
        }
        RefundPolicy policy = new RefundPolicy(name.trim());
        rules.forEach(rule -> policy.addRule(rule.minimumMinutesBefore(), rule.refundPercent()));
        store.persist(policy);
        return policy.getId();
    }

    @Transactional
    public long createShowing(long movieId, long screenId, long refundPolicyId, Instant startsAt,
                              long basePriceCents, int weekendMultiplierBps) {
        Movie movie = require(store.movie(movieId), "MOVIE_NOT_FOUND");
        Screen screen = require(store.screen(screenId), "SCREEN_NOT_FOUND");
        RefundPolicy policy = require(store.refundPolicy(refundPolicyId), "REFUND_POLICY_NOT_FOUND");
        if (!startsAt.isAfter(clock.instant())) {
            throw ApiException.badRequest("SHOWING_IN_PAST", "A showing must start in the future");
        }
        ensureScreenAvailable(screenId, startsAt, movie.getDurationMinutes());
        Showing showing = store.persist(new Showing(movie, screen, policy,
                OffsetDateTime.ofInstant(startsAt, ZoneOffset.UTC), basePriceCents, weekendMultiplierBps));
        ZonedDateTime localStart = startsAt.atZone(ZoneId.of(screen.getTheater().getCity().getTimezone()));
        boolean weekend = localStart.getDayOfWeek() == DayOfWeek.SATURDAY
                || localStart.getDayOfWeek() == DayOfWeek.SUNDAY;
        int weekendMultiplier = weekend ? weekendMultiplierBps : 10000;

        List<Seat> seats = store.screenSeats(screenId);
        if (seats.isEmpty()) {
            throw ApiException.badRequest("EMPTY_SCREEN", "Add seats before scheduling a showing");
        }
        List<ShowSeat> inventory = seats.stream().map(seat -> {
            PricingTier tier = store.pricingTier(seat.getCategory());
            long tierPrice = Math.multiplyExact(basePriceCents, tier.getMultiplierBps()) / 10000;
            long finalPrice = Math.multiplyExact(tierPrice, weekendMultiplier) / 10000;
            return new ShowSeat(showing, seat, finalPrice);
        }).toList();
        store.persistAll(inventory);
        return showing.getId();
    }

    @Transactional
    public void createDiscount(String code, String kind, long valueAmount, long minimumOrderCents,
                               Instant validFrom, Instant validUntil, Integer maxRedemptions) {
        String normalizedKind = kind.toUpperCase();
        if (!Set.of("PERCENT", "FIXED").contains(normalizedKind)) {
            throw ApiException.badRequest("INVALID_DISCOUNT_KIND", "Kind must be PERCENT or FIXED");
        }
        if (normalizedKind.equals("PERCENT") && valueAmount > 100) {
            throw ApiException.badRequest("INVALID_PERCENT", "Percentage discount cannot exceed 100");
        }
        store.persist(new DiscountCode(code.toUpperCase(), normalizedKind, valueAmount,
                minimumOrderCents, OffsetDateTime.ofInstant(validFrom, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(validUntil, ZoneOffset.UTC), maxRedemptions));
    }

    private void ensureScreenAvailable(long screenId, Instant startsAt, int durationMinutes) {
        Instant newEnd = startsAt.plus(Duration.ofMinutes(durationMinutes));
        boolean overlap = store.scheduledShowings(screenId).stream().anyMatch(showing -> {
            Instant existingStart = showing.getStartsAt().toInstant();
            Instant existingEnd = existingStart.plus(Duration.ofMinutes(showing.getMovie().getDurationMinutes()));
            return startsAt.isBefore(existingEnd) && existingStart.isBefore(newEnd);
        });
        if (overlap) {
            throw ApiException.conflict("SCREEN_SCHEDULE_CONFLICT", "The screen is occupied at this time");
        }
    }

    private <T> T require(T entity, String code) {
        if (entity == null) throw ApiException.notFound(code, "Referenced resource not found");
        return entity;
    }

    public record CityView(long id, String name, String timezone) { }
    public record MovieView(long id, String title, int durationMinutes, String certificate, String language) { }
    public record ShowingView(long id, long movieId, String movieTitle, long cityId, String cityName,
                              String theaterName, String screenName, OffsetDateTime startsAt,
                              long priceFromCents) { }
    public record SeatView(long showSeatId, String label, String category, long priceCents, String status) { }
    public record ScreenCreated(long id, int seatCount) { }
    public record RefundRuleInput(long minimumMinutesBefore, int refundPercent) { }
}
