package com.mayankbhati.movietickets.catalog.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mayankbhati.movietickets.catalog.application.CatalogService;
import com.mayankbhati.movietickets.catalog.application.CatalogService.CityView;
import com.mayankbhati.movietickets.catalog.application.CatalogService.MovieView;
import com.mayankbhati.movietickets.catalog.application.CatalogService.SeatView;
import com.mayankbhati.movietickets.catalog.application.CatalogService.ShowingView;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/cities")
    List<CityView> cities() {
        return catalog.cities();
    }

    @GetMapping("/movies")
    List<MovieView> movies() {
        return catalog.movies();
    }

    @GetMapping("/showings")
    List<ShowingView> showings(@RequestParam(required = false) Long cityId,
                               @RequestParam(required = false) Long movieId) {
        return catalog.showings(cityId, movieId);
    }

    @GetMapping("/showings/{showingId}/seats")
    List<SeatView> seats(@PathVariable long showingId) {
        return catalog.seats(showingId);
    }
}

