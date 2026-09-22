package com.mayankbhati.movietickets.booking.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mayankbhati.movietickets.booking.application.BookingService;
import com.mayankbhati.movietickets.booking.application.BookingService.BookingSummary;
import com.mayankbhati.movietickets.booking.application.BookingService.BookingView;
import com.mayankbhati.movietickets.booking.application.BookingService.CancellationView;
import com.mayankbhati.movietickets.booking.application.BookingService.HoldView;
import com.mayankbhati.movietickets.identity.application.IdentityService;
import com.mayankbhati.movietickets.identity.domain.Actor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
public class BookingController {
    private final BookingService bookings;
    private final IdentityService identity;

    public BookingController(BookingService bookings, IdentityService identity) {
        this.bookings = bookings;
        this.identity = identity;
    }

    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    HoldView hold(@Valid @RequestBody CreateHold request) {
        return bookings.createHold(identity.currentActor(), request.showingId(), request.showSeatIds());
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    BookingView book(@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 80) String idempotencyKey,
                     @Valid @RequestBody ConfirmBooking request) {
        return bookings.confirm(identity.currentActor(), request.holdId(), request.discountCode(),
                request.paymentMethod(), idempotencyKey);
    }

    @GetMapping("/bookings")
    List<BookingSummary> history() {
        return bookings.history(identity.currentActor());
    }

    @GetMapping("/bookings/{bookingId}")
    BookingView booking(@PathVariable String bookingId) {
        Actor actor = identity.currentActor();
        return bookings.booking(actor.id(), bookingId);
    }

    @DeleteMapping("/bookings/{bookingId}")
    CancellationView cancel(@PathVariable String bookingId) {
        return bookings.cancel(identity.currentActor(), bookingId);
    }

    public record CreateHold(@Min(1) long showingId,
                             @NotEmpty @Size(max = 10) List<@Min(1) Long> showSeatIds) {
    }

    public record ConfirmBooking(@NotBlank String holdId, @Size(max = 40) String discountCode,
                                 @NotBlank @Size(max = 100) String paymentMethod) {
    }
}

