package com.moviebooking.catalog;

import com.moviebooking.catalog.CatalogDtos.CityResponse;
import com.moviebooking.catalog.CatalogDtos.MovieResponse;
import com.moviebooking.catalog.CatalogDtos.ShowResponse;
import com.moviebooking.catalog.CatalogDtos.ShowSeatResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public browse endpoints for customers (no authentication required). */
@RestController
public class BrowseController {

    private final CatalogService catalogService;
    private final ShowService showService;

    public BrowseController(CatalogService catalogService, ShowService showService) {
        this.catalogService = catalogService;
        this.showService = showService;
    }

    @GetMapping("/api/cities")
    public List<CityResponse> cities() {
        return catalogService.listCities();
    }

    @GetMapping("/api/movies")
    public List<MovieResponse> movies() {
        return catalogService.listMovies();
    }

    @GetMapping("/api/movies/{id}")
    public MovieResponse movie(@PathVariable Long id) {
        return catalogService.getMovie(id);
    }

    @GetMapping("/api/movies/{id}/shows")
    public List<ShowResponse> showsForMovie(@PathVariable Long id,
                                            @RequestParam(required = false) Long cityId) {
        return showService.listShowsForMovie(id, cityId);
    }

    @GetMapping("/api/shows/{id}")
    public ShowResponse show(@PathVariable Long id) {
        return showService.getShow(id);
    }

    @GetMapping("/api/shows/{id}/seats")
    public List<ShowSeatResponse> seatMap(@PathVariable Long id) {
        return showService.seatMap(id);
    }
}
