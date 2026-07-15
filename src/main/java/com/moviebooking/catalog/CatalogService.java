package com.moviebooking.catalog;

import com.moviebooking.catalog.CatalogDtos.CityRequest;
import com.moviebooking.catalog.CatalogDtos.CityResponse;
import com.moviebooking.catalog.CatalogDtos.MovieRequest;
import com.moviebooking.catalog.CatalogDtos.MovieResponse;
import com.moviebooking.catalog.CatalogDtos.ScreenRequest;
import com.moviebooking.catalog.CatalogDtos.ScreenResponse;
import com.moviebooking.catalog.CatalogDtos.SeatResponse;
import com.moviebooking.catalog.CatalogDtos.SeatRow;
import com.moviebooking.catalog.CatalogDtos.TheaterRequest;
import com.moviebooking.catalog.CatalogDtos.TheaterResponse;
import com.moviebooking.entity.City;
import com.moviebooking.entity.Movie;
import com.moviebooking.entity.Screen;
import com.moviebooking.entity.Seat;
import com.moviebooking.entity.Theater;
import com.moviebooking.repository.CityRepository;
import com.moviebooking.repository.MovieRepository;
import com.moviebooking.repository.ScreenRepository;
import com.moviebooking.repository.SeatRepository;
import com.moviebooking.repository.TheaterRepository;
import com.moviebooking.web.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final CityRepository cityRepository;
    private final TheaterRepository theaterRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final MovieRepository movieRepository;

    public CatalogService(CityRepository cityRepository, TheaterRepository theaterRepository,
                          ScreenRepository screenRepository, SeatRepository seatRepository,
                          MovieRepository movieRepository) {
        this.cityRepository = cityRepository;
        this.theaterRepository = theaterRepository;
        this.screenRepository = screenRepository;
        this.seatRepository = seatRepository;
        this.movieRepository = movieRepository;
    }

    // ----- Cities -----
    @Transactional
    public CityResponse createCity(CityRequest request) {
        if (cityRepository.existsByNameIgnoreCase(request.name())) {
            throw ApiException.conflict("City already exists: " + request.name());
        }
        City city = new City();
        city.setName(request.name());
        return toCityResponse(cityRepository.save(city));
    }

    public List<CityResponse> listCities() {
        return cityRepository.findAll().stream().map(this::toCityResponse).toList();
    }

    @Transactional
    public void deleteCity(Long id) {
        if (!cityRepository.existsById(id)) {
            throw ApiException.notFound("City not found: " + id);
        }
        cityRepository.deleteById(id);
    }

    // ----- Theaters -----
    @Transactional
    public TheaterResponse createTheater(TheaterRequest request) {
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> ApiException.notFound("City not found: " + request.cityId()));
        Theater theater = new Theater();
        theater.setCity(city);
        theater.setName(request.name());
        theater.setAddress(request.address());
        return toTheaterResponse(theaterRepository.save(theater));
    }

    @Transactional(readOnly = true)
    public List<TheaterResponse> listTheaters(Long cityId) {
        List<Theater> theaters = cityId == null
                ? theaterRepository.findAll()
                : theaterRepository.findByCityId(cityId);
        return theaters.stream().map(this::toTheaterResponse).toList();
    }

    // ----- Screens + seat layout -----
    @Transactional
    public ScreenResponse createScreen(ScreenRequest request) {
        Theater theater = theaterRepository.findById(request.theaterId())
                .orElseThrow(() -> ApiException.notFound("Theater not found: " + request.theaterId()));
        Screen screen = new Screen();
        screen.setTheater(theater);
        screen.setName(request.name());
        screenRepository.save(screen);

        for (SeatRow row : request.rows()) {
            for (int number = 1; number <= row.seatCount(); number++) {
                Seat seat = new Seat();
                seat.setScreen(screen);
                seat.setRowLabel(row.rowLabel());
                seat.setSeatNumber(number);
                seat.setSeatType(row.seatType());
                seatRepository.save(seat);
            }
        }
        return getScreen(screen.getId());
    }

    @Transactional(readOnly = true)
    public ScreenResponse getScreen(Long id) {
        Screen screen = screenRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Screen not found: " + id));
        List<SeatResponse> seats = seatRepository.findByScreenIdOrderByRowLabelAscSeatNumberAsc(id).stream()
                .map(s -> new SeatResponse(s.getId(), s.getRowLabel(), s.getSeatNumber(), s.getSeatType().name()))
                .toList();
        return new ScreenResponse(screen.getId(), screen.getTheater().getId(), screen.getName(),
                seats.size(), seats);
    }

    @Transactional(readOnly = true)
    public List<ScreenResponse> listScreens(Long theaterId) {
        List<Screen> screens = theaterId == null
                ? screenRepository.findAll()
                : screenRepository.findByTheaterId(theaterId);
        return screens.stream().map(s -> getScreen(s.getId())).toList();
    }

    // ----- Movies -----
    @Transactional
    public MovieResponse createMovie(MovieRequest request) {
        Movie movie = new Movie();
        applyMovie(movie, request);
        return toMovieResponse(movieRepository.save(movie));
    }

    @Transactional
    public MovieResponse updateMovie(Long id, MovieRequest request) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Movie not found: " + id));
        applyMovie(movie, request);
        return toMovieResponse(movieRepository.save(movie));
    }

    public List<MovieResponse> listMovies() {
        return movieRepository.findAll().stream().map(this::toMovieResponse).toList();
    }

    public MovieResponse getMovie(Long id) {
        return movieRepository.findById(id).map(this::toMovieResponse)
                .orElseThrow(() -> ApiException.notFound("Movie not found: " + id));
    }

    @Transactional
    public void deleteMovie(Long id) {
        if (!movieRepository.existsById(id)) {
            throw ApiException.notFound("Movie not found: " + id);
        }
        movieRepository.deleteById(id);
    }

    private void applyMovie(Movie movie, MovieRequest request) {
        movie.setTitle(request.title());
        movie.setDescription(request.description());
        movie.setLanguage(request.language());
        movie.setGenre(request.genre());
        movie.setDurationMinutes(request.durationMinutes());
        movie.setRating(request.rating());
    }

    private CityResponse toCityResponse(City city) {
        return new CityResponse(city.getId(), city.getName());
    }

    private TheaterResponse toTheaterResponse(Theater theater) {
        return new TheaterResponse(theater.getId(), theater.getCity().getId(),
                theater.getName(), theater.getAddress());
    }

    private MovieResponse toMovieResponse(Movie movie) {
        return new MovieResponse(movie.getId(), movie.getTitle(), movie.getDescription(),
                movie.getLanguage(), movie.getGenre(), movie.getDurationMinutes(), movie.getRating());
    }
}
