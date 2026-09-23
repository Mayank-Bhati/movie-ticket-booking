package com.mayankbhati.movietickets.catalog.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mayankbhati.movietickets.catalog.domain.Showing;

public interface ShowingRepository extends JpaRepository<Showing, Long> {
    @EntityGraph(attributePaths = {"movie", "screen", "screen.theater", "screen.theater.city"})
    List<Showing> findByStatusAndStartsAtAfterOrderByStartsAtAsc(String status, OffsetDateTime now);

    @EntityGraph(attributePaths = {"movie", "screen", "screen.theater", "screen.theater.city"})
    List<Showing> findByStatusAndStartsAtAfterAndMovieIdOrderByStartsAtAsc(
            String status, OffsetDateTime now, long movieId);

    @EntityGraph(attributePaths = {"movie", "screen", "screen.theater", "screen.theater.city"})
    List<Showing> findByStatusAndStartsAtAfterAndScreenTheaterCityIdOrderByStartsAtAsc(
            String status, OffsetDateTime now, long cityId);

    @EntityGraph(attributePaths = {"movie", "screen", "screen.theater", "screen.theater.city"})
    List<Showing> findByStatusAndStartsAtAfterAndMovieIdAndScreenTheaterCityIdOrderByStartsAtAsc(
            String status, OffsetDateTime now, long movieId, long cityId);

    @EntityGraph(attributePaths = "movie")
    List<Showing> findByScreenIdAndStatus(long screenId, String status);
}
