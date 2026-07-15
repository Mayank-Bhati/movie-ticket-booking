package com.moviebooking.catalog;

import com.moviebooking.catalog.CatalogDtos.ShowRequest;
import com.moviebooking.catalog.CatalogDtos.ShowResponse;
import com.moviebooking.catalog.CatalogDtos.ShowSeatResponse;
import com.moviebooking.entity.Movie;
import com.moviebooking.entity.Screen;
import com.moviebooking.entity.Seat;
import com.moviebooking.entity.Show;
import com.moviebooking.entity.ShowSeat;
import com.moviebooking.pricing.PricingService;
import com.moviebooking.repository.MovieRepository;
import com.moviebooking.repository.ScreenRepository;
import com.moviebooking.repository.SeatRepository;
import com.moviebooking.repository.ShowRepository;
import com.moviebooking.repository.ShowSeatRepository;
import com.moviebooking.web.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final ShowSeatRepository showSeatRepository;
    private final PricingService pricingService;

    public ShowService(ShowRepository showRepository, MovieRepository movieRepository,
                       ScreenRepository screenRepository, SeatRepository seatRepository,
                       ShowSeatRepository showSeatRepository, PricingService pricingService) {
        this.showRepository = showRepository;
        this.movieRepository = movieRepository;
        this.screenRepository = screenRepository;
        this.seatRepository = seatRepository;
        this.showSeatRepository = showSeatRepository;
        this.pricingService = pricingService;
    }

    /** Creates a show and materializes one show_seat per seat in the screen, priced up front. */
    @Transactional
    public ShowResponse createShow(ShowRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw ApiException.badRequest("Show end time must be after start time");
        }
        Movie movie = movieRepository.findById(request.movieId())
                .orElseThrow(() -> ApiException.notFound("Movie not found: " + request.movieId()));
        Screen screen = screenRepository.findById(request.screenId())
                .orElseThrow(() -> ApiException.notFound("Screen not found: " + request.screenId()));

        List<Seat> seats = seatRepository.findByScreenIdOrderByRowLabelAscSeatNumberAsc(screen.getId());
        if (seats.isEmpty()) {
            throw ApiException.badRequest("Screen has no seats defined");
        }

        Show show = new Show();
        show.setMovie(movie);
        show.setScreen(screen);
        show.setStartsAt(request.startsAt());
        show.setEndsAt(request.endsAt());
        show.setBasePrice(request.basePrice());
        showRepository.save(show);

        for (Seat seat : seats) {
            ShowSeat showSeat = new ShowSeat();
            showSeat.setShow(show);
            showSeat.setSeat(seat);
            showSeat.setStatus(ShowSeat.Status.AVAILABLE);
            showSeat.setPrice(pricingService.priceFor(show, seat));
            showSeatRepository.save(showSeat);
        }
        return toShowResponse(show, seats.size());
    }

    @Transactional(readOnly = true)
    public List<ShowResponse> listShowsForMovie(Long movieId, Long cityId) {
        List<Show> shows = cityId == null
                ? showRepository.findByMovieId(movieId)
                : showRepository.findByMovieAndCity(movieId, cityId);
        return shows.stream()
                .map(s -> toShowResponse(s, (int) seatRepository.countByScreenId(s.getScreen().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(Long id) {
        Show show = showRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Show not found: " + id));
        return toShowResponse(show, (int) seatRepository.countByScreenId(show.getScreen().getId()));
    }

    /** Live seat map for a show: each seat with its current status and price. */
    @Transactional(readOnly = true)
    public List<ShowSeatResponse> seatMap(Long showId) {
        if (!showRepository.existsById(showId)) {
            throw ApiException.notFound("Show not found: " + showId);
        }
        return showSeatRepository.findSeatMapByShowId(showId).stream()
                .map(ss -> {
                    Seat seat = ss.getSeat();
                    return new ShowSeatResponse(ss.getId(), seat.getRowLabel(), seat.getSeatNumber(),
                            seat.getSeatType().name(), ss.getStatus().name(), ss.getPrice());
                })
                .toList();
    }

    private ShowResponse toShowResponse(Show show, int totalSeats) {
        return new ShowResponse(show.getId(), show.getMovie().getId(), show.getMovie().getTitle(),
                show.getScreen().getId(), show.getStartsAt(), show.getEndsAt(), show.getBasePrice(),
                totalSeats);
    }
}
