package com.mayankbhati.movietickets.catalog.application;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mayankbhati.movietickets.shared.ApiException;

@Service
public class CatalogService {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public CatalogService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public List<CityView> cities() {
        return jdbc.query("SELECT id, name, timezone FROM city ORDER BY name", Map.of(),
                (rs, rowNum) -> new CityView(rs.getLong("id"), rs.getString("name"),
                        rs.getString("timezone")));
    }

    public List<MovieView> movies() {
        return jdbc.query("""
                SELECT id, title, duration_minutes, certificate, language
                FROM movie ORDER BY title
                """, Map.of(), (rs, rowNum) -> new MovieView(rs.getLong("id"),
                rs.getString("title"), rs.getInt("duration_minutes"), rs.getString("certificate"),
                rs.getString("language")));
    }

    public List<ShowingView> showings(Long cityId, Long movieId) {
        StringBuilder sql = new StringBuilder("""
                SELECT sh.id, m.id AS movie_id, m.title, c.id AS city_id, c.name AS city_name,
                       t.name AS theater_name, sc.name AS screen_name, sh.starts_at,
                       MIN(ss.price_cents) AS price_from_cents
                FROM showing sh
                JOIN movie m ON m.id = sh.movie_id
                JOIN screen sc ON sc.id = sh.screen_id
                JOIN theater t ON t.id = sc.theater_id
                JOIN city c ON c.id = t.city_id
                JOIN show_seat ss ON ss.showing_id = sh.id
                WHERE sh.status = 'SCHEDULED' AND sh.starts_at > :now
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource("now", OffsetDateTime.now(clock));
        if (cityId != null) {
            sql.append(" AND c.id = :cityId");
            parameters.addValue("cityId", cityId);
        }
        if (movieId != null) {
            sql.append(" AND m.id = :movieId");
            parameters.addValue("movieId", movieId);
        }
        sql.append("""
                 GROUP BY sh.id, m.id, m.title, c.id, c.name, t.name, sc.name, sh.starts_at
                 ORDER BY sh.starts_at
                """);
        return jdbc.query(sql.toString(), parameters, (rs, rowNum) -> new ShowingView(
                rs.getLong("id"), rs.getLong("movie_id"), rs.getString("title"),
                rs.getLong("city_id"), rs.getString("city_name"), rs.getString("theater_name"),
                rs.getString("screen_name"), rs.getObject("starts_at", OffsetDateTime.class),
                rs.getLong("price_from_cents")));
    }

    @Transactional
    public List<SeatView> seats(long showingId) {
        requireExists("showing", showingId, "SHOWING_NOT_FOUND");
        OffsetDateTime now = OffsetDateTime.now(clock);
        return jdbc.query("""
                SELECT ss.id, s.row_label, s.seat_number, ss.category, ss.price_cents,
                       CASE WHEN ss.status = 'HELD' AND ss.hold_expires_at <= :now
                            THEN 'AVAILABLE' ELSE ss.status END AS visible_status
                FROM show_seat ss
                JOIN seat s ON s.id = ss.seat_id
                WHERE ss.showing_id = :showingId
                ORDER BY s.row_label, s.seat_number
                """, Map.of("showingId", showingId, "now", now), (rs, rowNum) -> new SeatView(
                rs.getLong("id"), rs.getString("row_label") + rs.getInt("seat_number"),
                rs.getString("category"), rs.getLong("price_cents"),
                rs.getString("visible_status")));
    }

    @Transactional
    public long createCity(String name, String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (Exception exception) {
            throw ApiException.badRequest("INVALID_TIMEZONE", "Timezone must be a valid IANA zone");
        }
        return insert("INSERT INTO city(name, timezone) VALUES (:name, :timezone)",
                new MapSqlParameterSource().addValue("name", name.trim()).addValue("timezone", timezone));
    }

    @Transactional
    public long createTheater(long cityId, String name, String address) {
        requireExists("city", cityId, "CITY_NOT_FOUND");
        return insert("""
                INSERT INTO theater(city_id, name, address)
                VALUES (:cityId, :name, :address)
                """, new MapSqlParameterSource().addValue("cityId", cityId)
                .addValue("name", name.trim()).addValue("address", address.trim()));
    }

    @Transactional
    public ScreenCreated createScreen(long theaterId, String name, int rows, int seatsPerRow,
                                      Set<Integer> premiumRows) {
        requireExists("theater", theaterId, "THEATER_NOT_FOUND");
        if (rows > 26) {
            throw ApiException.badRequest("TOO_MANY_ROWS", "A screen supports at most 26 rows");
        }
        for (Integer premiumRow : premiumRows) {
            if (premiumRow < 1 || premiumRow > rows) {
                throw ApiException.badRequest("INVALID_PREMIUM_ROW", "Premium rows must exist in the layout");
            }
        }
        long screenId = insert("""
                INSERT INTO screen(theater_id, name) VALUES (:theaterId, :name)
                """, new MapSqlParameterSource().addValue("theaterId", theaterId).addValue("name", name.trim()));

        List<MapSqlParameterSource> seats = new ArrayList<>();
        for (int row = 1; row <= rows; row++) {
            String rowLabel = String.valueOf((char) ('A' + row - 1));
            String category = premiumRows.contains(row) ? "PREMIUM" : "REGULAR";
            for (int seatNumber = 1; seatNumber <= seatsPerRow; seatNumber++) {
                seats.add(new MapSqlParameterSource().addValue("screenId", screenId)
                        .addValue("rowLabel", rowLabel).addValue("seatNumber", seatNumber)
                        .addValue("category", category));
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO seat(screen_id, row_label, seat_number, category)
                VALUES (:screenId, :rowLabel, :seatNumber, :category)
                """, seats.toArray(MapSqlParameterSource[]::new));
        return new ScreenCreated(screenId, seats.size());
    }

    @Transactional
    public long createMovie(String title, int durationMinutes, String certificate, String language) {
        return insert("""
                INSERT INTO movie(title, duration_minutes, certificate, language)
                VALUES (:title, :duration, :certificate, :language)
                """, new MapSqlParameterSource().addValue("title", title.trim())
                .addValue("duration", durationMinutes).addValue("certificate", certificate)
                .addValue("language", language.trim()));
    }

    @Transactional
    public void updatePricingTier(String category, int multiplierBps) {
        String normalized = category.toUpperCase();
        if (!Set.of("REGULAR", "PREMIUM").contains(normalized)) {
            throw ApiException.badRequest("INVALID_SEAT_CATEGORY", "Category must be REGULAR or PREMIUM");
        }
        jdbc.update("UPDATE pricing_tier SET multiplier_bps = :multiplier WHERE category = :category",
                Map.of("multiplier", multiplierBps, "category", normalized));
    }

    @Transactional
    public long createRefundPolicy(String name, List<RefundRuleInput> rules) {
        Set<Long> thresholds = new HashSet<>();
        if (rules.isEmpty() || rules.stream().anyMatch(rule -> !thresholds.add(rule.minimumMinutesBefore()))) {
            throw ApiException.badRequest("INVALID_REFUND_RULES",
                    "Provide at least one rule with unique minute thresholds");
        }
        long policyId = insert("INSERT INTO refund_policy(name) VALUES (:name)",
                new MapSqlParameterSource("name", name.trim()));
        List<MapSqlParameterSource> batch = rules.stream().map(rule -> new MapSqlParameterSource()
                .addValue("policyId", policyId).addValue("minutes", rule.minimumMinutesBefore())
                .addValue("percent", rule.refundPercent())).toList();
        jdbc.batchUpdate("""
                INSERT INTO refund_rule(policy_id, minimum_minutes_before, refund_percent)
                VALUES (:policyId, :minutes, :percent)
                """, batch.toArray(MapSqlParameterSource[]::new));
        return policyId;
    }

    @Transactional
    public long createShowing(long movieId, long screenId, long refundPolicyId, Instant startsAt,
                              long basePriceCents, int weekendMultiplierBps) {
        MovieDuration movie = jdbc.query("SELECT duration_minutes FROM movie WHERE id = :id",
                Map.of("id", movieId), (rs, rowNum) -> new MovieDuration(rs.getInt(1)))
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("MOVIE_NOT_FOUND", "Movie not found"));
        requireExists("screen", screenId, "SCREEN_NOT_FOUND");
        requireExists("refund_policy", refundPolicyId, "REFUND_POLICY_NOT_FOUND");
        if (!startsAt.isAfter(clock.instant())) {
            throw ApiException.badRequest("SHOWING_IN_PAST", "A showing must start in the future");
        }
        ensureScreenAvailable(screenId, startsAt, movie.durationMinutes());

        long showingId = insert("""
                INSERT INTO showing(movie_id, screen_id, refund_policy_id, starts_at,
                                    base_price_cents, weekend_multiplier_bps, status)
                VALUES (:movieId, :screenId, :policyId, :startsAt, :basePrice,
                        :weekendMultiplier, 'SCHEDULED')
                """, new MapSqlParameterSource().addValue("movieId", movieId)
                .addValue("screenId", screenId).addValue("policyId", refundPolicyId)
                .addValue("startsAt", OffsetDateTime.ofInstant(startsAt, ZoneId.of("UTC")))
                .addValue("basePrice", basePriceCents).addValue("weekendMultiplier", weekendMultiplierBps));

        String timezone = jdbc.queryForObject("""
                SELECT c.timezone FROM screen sc
                JOIN theater t ON t.id = sc.theater_id
                JOIN city c ON c.id = t.city_id
                WHERE sc.id = :screenId
                """, Map.of("screenId", screenId), String.class);
        ZonedDateTime localStart = startsAt.atZone(ZoneId.of(timezone));
        boolean weekend = localStart.getDayOfWeek() == DayOfWeek.SATURDAY
                || localStart.getDayOfWeek() == DayOfWeek.SUNDAY;
        int effectiveWeekendMultiplier = weekend ? weekendMultiplierBps : 10000;

        List<SeatForShow> seats = jdbc.query("""
                SELECT s.id, s.category, p.multiplier_bps
                FROM seat s JOIN pricing_tier p ON p.category = s.category
                WHERE s.screen_id = :screenId ORDER BY s.id
                """, Map.of("screenId", screenId), (rs, rowNum) -> new SeatForShow(
                rs.getLong("id"), rs.getString("category"), rs.getInt("multiplier_bps")));
        if (seats.isEmpty()) {
            throw ApiException.badRequest("EMPTY_SCREEN", "Add seats before scheduling a showing");
        }
        List<MapSqlParameterSource> inventory = seats.stream().map(seat -> {
            long tierPrice = Math.multiplyExact(basePriceCents, seat.multiplierBps()) / 10000;
            long finalPrice = Math.multiplyExact(tierPrice, effectiveWeekendMultiplier) / 10000;
            return new MapSqlParameterSource().addValue("showingId", showingId)
                    .addValue("seatId", seat.id()).addValue("category", seat.category())
                    .addValue("price", finalPrice);
        }).toList();
        jdbc.batchUpdate("""
                INSERT INTO show_seat(showing_id, seat_id, category, price_cents, status)
                VALUES (:showingId, :seatId, :category, :price, 'AVAILABLE')
                """, inventory.toArray(MapSqlParameterSource[]::new));
        return showingId;
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
        jdbc.update("""
                INSERT INTO discount_code(code, kind, value_amount, minimum_order_cents, valid_from,
                                          valid_until, max_redemptions, redemption_count, active)
                VALUES (:code, :kind, :value, :minimum, :validFrom, :validUntil, :max, 0, TRUE)
                """, new MapSqlParameterSource().addValue("code", code.toUpperCase())
                .addValue("kind", normalizedKind).addValue("value", valueAmount)
                .addValue("minimum", minimumOrderCents)
                .addValue("validFrom", OffsetDateTime.ofInstant(validFrom, ZoneId.of("UTC")))
                .addValue("validUntil", OffsetDateTime.ofInstant(validUntil, ZoneId.of("UTC")))
                .addValue("max", maxRedemptions));
    }

    private void ensureScreenAvailable(long screenId, Instant startsAt, int durationMinutes) {
        Instant newEnd = startsAt.plusSeconds(durationMinutes * 60L);
        List<ExistingShowing> existing = jdbc.query("""
                SELECT sh.starts_at, m.duration_minutes
                FROM showing sh JOIN movie m ON m.id = sh.movie_id
                WHERE sh.screen_id = :screenId AND sh.status = 'SCHEDULED'
                """, Map.of("screenId", screenId), (rs, rowNum) -> new ExistingShowing(
                rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                rs.getInt("duration_minutes")));
        boolean overlap = existing.stream().anyMatch(showing -> startsAt.isBefore(
                showing.startsAt().plusSeconds(showing.durationMinutes() * 60L))
                && showing.startsAt().isBefore(newEnd));
        if (overlap) {
            throw ApiException.conflict("SCREEN_SCHEDULE_CONFLICT", "The screen is occupied at this time");
        }
    }

    private void requireExists(String table, long id, String errorCode) {
        Set<String> allowed = Set.of("city", "theater", "screen", "movie", "refund_policy", "showing");
        if (!allowed.contains(table)) {
            throw new IllegalArgumentException("Unsupported table");
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = :id",
                Map.of("id", id), Integer.class);
        if (count == null || count == 0) {
            throw ApiException.notFound(errorCode, "Referenced resource not found");
        }
    }

    private long insert(String sql, MapSqlParameterSource parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, parameters, keyHolder, new String[]{"id"});
        if (keyHolder.getKey() == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "KEY_GENERATION_FAILED",
                    "Database did not return a generated identifier");
        }
        return keyHolder.getKey().longValue();
    }

    public record CityView(long id, String name, String timezone) {
    }

    public record MovieView(long id, String title, int durationMinutes, String certificate, String language) {
    }

    public record ShowingView(long id, long movieId, String movieTitle, long cityId, String cityName,
                              String theaterName, String screenName, OffsetDateTime startsAt,
                              long priceFromCents) {
    }

    public record SeatView(long showSeatId, String label, String category, long priceCents, String status) {
    }

    public record ScreenCreated(long id, int seatCount) {
    }

    public record RefundRuleInput(long minimumMinutesBefore, int refundPercent) {
    }

    private record MovieDuration(int durationMinutes) {
    }

    private record ExistingShowing(Instant startsAt, int durationMinutes) {
    }

    private record SeatForShow(long id, String category, int multiplierBps) {
    }
}
