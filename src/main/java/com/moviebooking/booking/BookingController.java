package com.moviebooking.booking;

import com.moviebooking.booking.BookingDtos.BookingRequest;
import com.moviebooking.booking.BookingDtos.BookingResponse;
import com.moviebooking.booking.BookingDtos.HoldRequest;
import com.moviebooking.booking.BookingDtos.HoldResponse;
import com.moviebooking.refund.RefundDtos.CancellationResponse;
import com.moviebooking.security.AppUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {

    private final HoldService holdService;
    private final BookingService bookingService;

    public BookingController(HoldService holdService, BookingService bookingService) {
        this.holdService = holdService;
        this.bookingService = bookingService;
    }

    /** Places a time-bound hold on the chosen seats for a show; returns the hold id and expiry. */
    @PostMapping("/api/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse createHold(@AuthenticationPrincipal AppUserPrincipal user,
                                   @Valid @RequestBody HoldRequest request) {
        return holdService.createHold(user.getId(), request);
    }

    /** Confirms a booking from an active hold: applies any discount, charges payment, books the seats. */
    @PostMapping("/api/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse createBooking(@AuthenticationPrincipal AppUserPrincipal user,
                                         @Valid @RequestBody BookingRequest request) {
        return bookingService.confirmBooking(user.getId(), request);
    }

    /** Returns the current user's booking history, newest first. */
    @GetMapping("/api/bookings")
    public List<BookingResponse> listBookings(@AuthenticationPrincipal AppUserPrincipal user) {
        return bookingService.listBookings(user.getId());
    }

    /** Returns a single booking owned by the current user. */
    @GetMapping("/api/bookings/{id}")
    public BookingResponse getBooking(@AuthenticationPrincipal AppUserPrincipal user,
                                      @PathVariable Long id) {
        return bookingService.getBooking(user.getId(), id);
    }

    /** Cancels a confirmed booking and returns the refund computed from the refund policy. */
    @DeleteMapping("/api/bookings/{id}")
    public CancellationResponse cancelBooking(@AuthenticationPrincipal AppUserPrincipal user,
                                              @PathVariable Long id) {
        return bookingService.cancelBooking(user.getId(), id);
    }
}
