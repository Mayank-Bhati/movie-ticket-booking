package com.moviebooking.repository;

import com.moviebooking.entity.Show;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShowRepository extends JpaRepository<Show, Long> {

    List<Show> findByMovieId(Long movieId);

    @Query("""
            SELECT s FROM Show s
            WHERE s.movie.id = :movieId
              AND s.screen.theater.city.id = :cityId
            ORDER BY s.startsAt ASC
            """)
    List<Show> findByMovieAndCity(@Param("movieId") Long movieId, @Param("cityId") Long cityId);
}
