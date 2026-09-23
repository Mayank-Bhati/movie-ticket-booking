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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.mayankbhati.movietickets.catalog.infrastructure.CityRepository;
import com.mayankbhati.movietickets.catalog.infrastructure.DiscountCodeRepository;
import com.mayankbhati.movietickets.catalog.infrastructure.MovieRepository;
import com.mayankbhati.movietickets.catalog.infrastructure.PricingTierRepository;
import com.mayankbhati.movietickets.catalog.infrastructure.RefundPolicyRepository;
import com.mayankbhati.movietickets.catalog.infrastructure.ScreenRepository;
import com.mayankbhati.movietickets.catalog.infrastructure.SeatRepository;
import com.mayankbhati.movietickets.catalog.infrastructure.ShowSeatRepository;
import com.mayankbhati.movietickets.catalog.infrastructure.ShowingRepository;
import com.mayankbhati.movietickets.catalog.infrastructure.TheaterRepository;
import com.mayankbhati.movietickets.shared.ApiException;

@Service
public class CatalogService {
    private final CityRepository cities;
    private final TheaterRepository theaters;
    private final ScreenRepository screens;
    private final SeatRepository seats;
    private final MovieRepository movies;
    private final PricingTierRepository pricingTiers;
    private final RefundPolicyRepository refundPolicies;
    private final ShowingRepository showings;
    private final ShowSeatRepository showSeats;
    private final DiscountCodeRepository discounts;
    private final Clock clock;

    public CatalogService(CityRepository cities, TheaterRepository theaters, ScreenRepository screens,
                          SeatRepository seats, MovieRepository movies, PricingTierRepository pricingTiers,
                          RefundPolicyRepository refundPolicies, ShowingRepository showings,
                          ShowSeatRepository showSeats, DiscountCodeRepository discounts, Clock clock) {
        this.cities = cities;
        this.theaters = theaters;
        this.screens = screens;
        this.seats = seats;
        this.movies = movies;
        this.pricingTiers = pricingTiers;
        this.refundPolicies = refundPolicies;
        this.showings = showings;
        this.showSeats = showSeats;
        this.discounts = discounts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CityView> cities() {
        return cities.findAllByOrderByNameAsc().stream()
                .map(city -> new CityView(city.getId(), city.getName(), city.getTimezone())).toList();
    }

    @Transactional(readOnly = true)
    public List<MovieView> movies() {
        return movies.findAllByOrderByTitleAsc().stream().map(movie -> new MovieView(movie.getId(),
                movie.getTitle(), movie.getDurationMinutes(), movie.getCertificate(), movie.getLanguage())).toList();
    }

    @Transactional(readOnly = true)
    public List<ShowingView> showings(Long cityId, Long movieId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Showing> matches;
        if (cityId != null && movieId != null) {
            matches = showings.findByStatusAndStartsAtAfterAndMovieIdAndScreenTheaterCityIdOrderByStartsAtAsc(
                    "SCHEDULED", now, movieId, cityId);
        } else if (cityId != null) {
            matches = showings.findByStatusAndStartsAtAfterAndScreenTheaterCityIdOrderByStartsAtAsc(
                    "SCHEDULED", now, cityId);
        } else if (movieId != null) {
            matches = showings.findByStatusAndStartsAtAfterAndMovieIdOrderByStartsAtAsc(
                    "SCHEDULED", now, movieId);
        } else {
            matches = showings.findByStatusAndStartsAtAfterOrderByStartsAtAsc("SCHEDULED", now);
        }

        Set<Long> showingIds = matches.stream().map(Showing::getId).collect(Collectors.toSet());
        Map<Long, Long> minimumPrices = showingIds.isEmpty() ? Map.of()
                : showSeats.findByShowingIdIn(showingIds).stream().collect(Collectors.toMap(
                        seat -> seat.getShowing().getId(), ShowSeat::getPriceCents, Math::min));
        return matches.stream().map(showing -> {
            Movie movie = showing.getMovie();
            Screen screen = showing.getScreen();
            Theater theater = screen.getTheater();
            City city = theater.getCity();
            return new ShowingView(showing.getId(), movie.getId(), movie.getTitle(), city.getId(),
                    city.getName(), theater.getName(), screen.getName(), showing.getStartsAt(),
                    minimumPrices.get(showing.getId()));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<SeatView> seats(long showingId) {
        require(showings.findById(showingId).orElse(null), "SHOWING_NOT_FOUND");
        OffsetDateTime now = OffsetDateTime.now(clock);
        return showSeats.findByShowingIdOrderBySeatRowLabelAscSeatSeatNumberAsc(showingId).stream()
                .map(seat -> new SeatView(seat.getId(), seat.getSeat().label(), seat.getCategory(),
                        seat.getPriceCents(), seat.visibleStatus(now))).toList();
    }

    @Transactional
    public long createCity(String name, String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (Exception exception) {
            throw ApiException.badRequest("INVALID_TIMEZONE", "Timezone must be a valid IANA zone");
        }
        return cities.save(new City(name.trim(), timezone)).getId();
    }

    @Transactional
    public long createTheater(long cityId, String name, String address) {
        City city = require(cities.findById(cityId).orElse(null), "CITY_NOT_FOUND");
        return theaters.save(new Theater(city, name.trim(), address.trim())).getId();
    }

    @Transactional
    public ScreenCreated createScreen(long theaterId, String name, int rows, int seatsPerRow,
                                      Set<Integer> premiumRows) {
        Theater theater = require(theaters.findById(theaterId).orElse(null), "THEATER_NOT_FOUND");
        if (rows > 26) {
            throw ApiException.badRequest("TOO_MANY_ROWS", "A screen supports at most 26 rows");
        }
        if (premiumRows.stream().anyMatch(row -> row < 1 || row > rows)) {
            throw ApiException.badRequest("INVALID_PREMIUM_ROW", "Premium rows must exist in the layout");
        }
        Screen screen = screens.save(new Screen(theater, name.trim()));
        List<Seat> layout = new ArrayList<>(rows * seatsPerRow);
        for (int row = 1; row <= rows; row++) {
            String rowLabel = String.valueOf((char) ('A' + row - 1));
            String category = premiumRows.contains(row) ? "PREMIUM" : "REGULAR";
            for (int seatNumber = 1; seatNumber <= seatsPerRow; seatNumber++) {
                layout.add(new Seat(screen, rowLabel, seatNumber, category));
            }
        }
        seats.saveAll(layout);
        return new ScreenCreated(screen.getId(), layout.size());
    }

    @Transactional
    public long createMovie(String title, int durationMinutes, String certificate, String language) {
        return movies.save(new Movie(title.trim(), durationMinutes, certificate, language.trim())).getId();
    }

    @Transactional
    public void updatePricingTier(String category, int multiplierBps) {
        PricingTier tier = pricingTiers.findById(category.toUpperCase()).orElseThrow(() ->
                ApiException.badRequest("INVALID_SEAT_CATEGORY", "Category must be REGULAR or PREMIUM"));
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
        return refundPolicies.save(policy).getId();
    }

    @Transactional
    public long createShowing(long movieId, long screenId, long refundPolicyId, Instant startsAt,
                              long basePriceCents, int weekendMultiplierBps) {
        Movie movie = require(movies.findById(movieId).orElse(null), "MOVIE_NOT_FOUND");
        Screen screen = require(screens.findById(screenId).orElse(null), "SCREEN_NOT_FOUND");
        RefundPolicy policy = require(refundPolicies.findById(refundPolicyId).orElse(null),
                "REFUND_POLICY_NOT_FOUND");
        if (!startsAt.isAfter(clock.instant())) {
            throw ApiException.badRequest("SHOWING_IN_PAST", "A showing must start in the future");
        }
        ensureScreenAvailable(screenId, startsAt, movie.getDurationMinutes());
        Showing showing = showings.save(new Showing(movie, screen, policy,
                OffsetDateTime.ofInstant(startsAt, ZoneOffset.UTC), basePriceCents, weekendMultiplierBps));
        ZonedDateTime localStart = startsAt.atZone(ZoneId.of(screen.getTheater().getCity().getTimezone()));
        boolean weekend = localStart.getDayOfWeek() == DayOfWeek.SATURDAY
                || localStart.getDayOfWeek() == DayOfWeek.SUNDAY;
        int weekendMultiplier = weekend ? weekendMultiplierBps : 10000;

        List<Seat> layout = seats.findByScreenIdOrderByIdAsc(screenId);
        if (layout.isEmpty()) {
            throw ApiException.badRequest("EMPTY_SCREEN", "Add seats before scheduling a showing");
        }
        List<ShowSeat> inventory = layout.stream().map(seat -> {
            PricingTier tier = pricingTiers.findById(seat.getCategory()).orElseThrow();
            long tierPrice = Math.multiplyExact(basePriceCents, tier.getMultiplierBps()) / 10000;
            return new ShowSeat(showing, seat,
                    Math.multiplyExact(tierPrice, weekendMultiplier) / 10000);
        }).toList();
        showSeats.saveAll(inventory);
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
        discounts.save(new DiscountCode(code.toUpperCase(), normalizedKind, valueAmount,
                minimumOrderCents, OffsetDateTime.ofInstant(validFrom, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(validUntil, ZoneOffset.UTC), maxRedemptions));
    }

    private void ensureScreenAvailable(long screenId, Instant startsAt, int durationMinutes) {
        Instant newEnd = startsAt.plus(Duration.ofMinutes(durationMinutes));
        boolean overlap = showings.findByScreenIdAndStatus(screenId, "SCHEDULED").stream().anyMatch(showing -> {
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
