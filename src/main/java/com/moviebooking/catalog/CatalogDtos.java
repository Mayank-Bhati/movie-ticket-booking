package com.moviebooking.catalog;

import com.moviebooking.entity.Seat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response payloads for the catalog (cities, theaters, screens, movies, shows). */
public class CatalogDtos {

    // ----- City -----
    public record CityRequest(@NotBlank String name) {
    }

    public record CityResponse(Long id, String name) {
    }

    // ----- Theater -----
    public record TheaterRequest(
            @NotNull Long cityId,
            @NotBlank String name,
            String address) {
    }

    public record TheaterResponse(Long id, Long cityId, String name, String address) {
    }

    // ----- Screen + seat layout -----
    public record SeatRow(
            @NotBlank String rowLabel,
            @NotNull Seat.SeatType seatType,
            @Min(1) int seatCount) {
    }

    public record ScreenRequest(
            @NotNull Long theaterId,
            @NotBlank String name,
            @NotEmpty @Valid List<SeatRow> rows) {
    }

    public record SeatResponse(Long id, String rowLabel, Integer seatNumber, String seatType) {
    }

    public record ScreenResponse(Long id, Long theaterId, String name, int seatCount, List<SeatResponse> seats) {
    }

    // ----- Movie -----
    public record MovieRequest(
            @NotBlank String title,
            String description,
            String language,
            String genre,
            @NotNull @Positive Integer durationMinutes,
            String rating) {
    }

    public record MovieResponse(Long id, String title, String description, String language,
                                String genre, Integer durationMinutes, String rating) {
    }

    // ----- Show -----
    public record ShowRequest(
            @NotNull Long movieId,
            @NotNull Long screenId,
            @NotNull @Future LocalDateTime startsAt,
            @NotNull LocalDateTime endsAt,
            @NotNull @Positive BigDecimal basePrice) {
    }

    public record ShowResponse(Long id, Long movieId, String movieTitle, Long screenId,
                               LocalDateTime startsAt, LocalDateTime endsAt, BigDecimal basePrice,
                               int totalSeats) {
    }

    // ----- Seat map (browse) -----
    public record ShowSeatResponse(Long showSeatId, String rowLabel, Integer seatNumber,
                                   String seatType, String status, BigDecimal price) {
    }
}
