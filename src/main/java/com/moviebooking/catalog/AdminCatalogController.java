package com.moviebooking.catalog;

import com.moviebooking.catalog.CatalogDtos.CityRequest;
import com.moviebooking.catalog.CatalogDtos.CityResponse;
import com.moviebooking.catalog.CatalogDtos.MovieRequest;
import com.moviebooking.catalog.CatalogDtos.MovieResponse;
import com.moviebooking.catalog.CatalogDtos.ScreenRequest;
import com.moviebooking.catalog.CatalogDtos.ScreenResponse;
import com.moviebooking.catalog.CatalogDtos.ShowRequest;
import com.moviebooking.catalog.CatalogDtos.ShowResponse;
import com.moviebooking.catalog.CatalogDtos.TheaterRequest;
import com.moviebooking.catalog.CatalogDtos.TheaterResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminCatalogController {

    private final CatalogService catalogService;
    private final ShowService showService;

    public AdminCatalogController(CatalogService catalogService, ShowService showService) {
        this.catalogService = catalogService;
        this.showService = showService;
    }

    // ----- Cities -----
    /** Creates a city. */
    @PostMapping("/cities")
    @ResponseStatus(HttpStatus.CREATED)
    public CityResponse createCity(@Valid @RequestBody CityRequest request) {
        return catalogService.createCity(request);
    }

    /** Deletes a city by id. */
    @DeleteMapping("/cities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCity(@PathVariable Long id) {
        catalogService.deleteCity(id);
    }

    // ----- Theaters -----
    /** Creates a theater within a city. */
    @PostMapping("/theaters")
    @ResponseStatus(HttpStatus.CREATED)
    public TheaterResponse createTheater(@Valid @RequestBody TheaterRequest request) {
        return catalogService.createTheater(request);
    }

    /** Lists theaters, optionally filtered by city. */
    @GetMapping("/theaters")
    public List<TheaterResponse> listTheaters(@RequestParam(required = false) Long cityId) {
        return catalogService.listTheaters(cityId);
    }

    // ----- Screens -----
    /** Creates a screen and generates its seat layout from the row specs. */
    @PostMapping("/screens")
    @ResponseStatus(HttpStatus.CREATED)
    public ScreenResponse createScreen(@Valid @RequestBody ScreenRequest request) {
        return catalogService.createScreen(request);
    }

    /** Lists screens, optionally filtered by theater. */
    @GetMapping("/screens")
    public List<ScreenResponse> listScreens(@RequestParam(required = false) Long theaterId) {
        return catalogService.listScreens(theaterId);
    }

    /** Returns a screen with its full seat layout. */
    @GetMapping("/screens/{id}")
    public ScreenResponse getScreen(@PathVariable Long id) {
        return catalogService.getScreen(id);
    }

    // ----- Movies -----
    /** Creates a movie. */
    @PostMapping("/movies")
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse createMovie(@Valid @RequestBody MovieRequest request) {
        return catalogService.createMovie(request);
    }

    /** Updates an existing movie. */
    @PutMapping("/movies/{id}")
    public MovieResponse updateMovie(@PathVariable Long id, @Valid @RequestBody MovieRequest request) {
        return catalogService.updateMovie(id, request);
    }

    /** Deletes a movie by id. */
    @DeleteMapping("/movies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMovie(@PathVariable Long id) {
        catalogService.deleteMovie(id);
    }

    // ----- Shows -----
    /** Creates a show and materializes a priced, bookable seat for every seat in the screen. */
    @PostMapping("/shows")
    @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse createShow(@Valid @RequestBody ShowRequest request) {
        return showService.createShow(request);
    }
}
