package com.moviebooking.booking;

import com.moviebooking.booking.BookingDtos.BookingRequest;
import com.moviebooking.booking.BookingDtos.BookingResponse;
import com.moviebooking.booking.BookingDtos.HeldSeat;
import com.moviebooking.booking.PaymentGateway.PaymentResult;
import com.moviebooking.entity.Booking;
import com.moviebooking.entity.BookingSeat;
import com.moviebooking.entity.Payment;
import com.moviebooking.entity.SeatHold;
import com.moviebooking.entity.ShowSeat;
import com.moviebooking.pricing.DiscountService;
import com.moviebooking.pricing.DiscountService.DiscountResult;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.PaymentRepository;
import com.moviebooking.repository.SeatHoldRepository;
import com.moviebooking.repository.ShowSeatRepository;
import com.moviebooking.web.ApiException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final SeatHoldRepository seatHoldRepository;
    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final DiscountService discountService;
    private final PaymentGateway paymentGateway;

    public BookingService(SeatHoldRepository seatHoldRepository, ShowSeatRepository showSeatRepository,
                          BookingRepository bookingRepository, PaymentRepository paymentRepository,
                          DiscountService discountService, PaymentGateway paymentGateway) {
        this.seatHoldRepository = seatHoldRepository;
        this.showSeatRepository = showSeatRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.discountService = discountService;
        this.paymentGateway = paymentGateway;
    }

    /**
     * Confirms a booking from an active hold: re-locks the held seats, applies any discount,
     * charges payment, and marks the seats BOOKED — all in one transaction. If payment fails
     * the whole transaction rolls back, leaving the hold and seats untouched so the customer
     * can retry until the hold expires.
     */
    @Transactional
    public BookingResponse confirmBooking(Long userId, BookingRequest request) {
        SeatHold hold = requireActiveOwnedHold(userId, request.holdId());
        List<ShowSeat> seats = lockHeldSeats(hold);

        BigDecimal subtotal = seats.stream()
                .map(ShowSeat::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        DiscountResult discount = resolveDiscount(request.discountCode(), subtotal);

        Booking booking = buildBooking(hold, seats, subtotal, discount);
        bookingRepository.save(booking);

        Payment payment = takePayment(booking);

        booking.setStatus(Booking.Status.CONFIRMED);
        seats.forEach(seat -> seat.setStatus(ShowSeat.Status.BOOKED));
        hold.setStatus(SeatHold.Status.CONVERTED);
        if (discount != null) {
            discount.code().setUsedCount(discount.code().getUsedCount() + 1);
        }
        return toResponse(booking, payment.getStatus().name());
    }

    /** Returns the user's bookings, newest first. */
    @Transactional(readOnly = true)
    public List<BookingResponse> listBookings(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(booking -> toResponse(booking, null))
                .toList();
    }

    /** Returns a single booking, enforcing ownership. */
    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found: " + bookingId));
        if (!booking.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("Booking does not belong to the current user");
        }
        return toResponse(booking, null);
    }

    // ----- confirmBooking steps -----

    private SeatHold requireActiveOwnedHold(Long userId, Long holdId) {
        SeatHold hold = seatHoldRepository.findById(holdId)
                .orElseThrow(() -> ApiException.notFound("Hold not found: " + holdId));
        if (!hold.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("Hold does not belong to the current user");
        }
        if (hold.getStatus() != SeatHold.Status.ACTIVE || hold.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw ApiException.gone("Hold has expired or is no longer active");
        }
        return hold;
    }

    private List<ShowSeat> lockHeldSeats(SeatHold hold) {
        List<ShowSeat> seats = showSeatRepository.lockByHoldId(hold.getId());
        if (seats.isEmpty()) {
            throw ApiException.gone("Held seats are no longer available");
        }
        for (ShowSeat seat : seats) {
            if (seat.getStatus() != ShowSeat.Status.HELD) {
                throw ApiException.conflict("Seat is no longer held: " + seat.getSeat().label());
            }
        }
        return seats;
    }

    private DiscountResult resolveDiscount(String code, BigDecimal subtotal) {
        return (code == null || code.isBlank()) ? null : discountService.apply(code, subtotal);
    }

    private Booking buildBooking(SeatHold hold, List<ShowSeat> seats,
                                 BigDecimal subtotal, DiscountResult discount) {
        BigDecimal discountAmount = discount == null ? BigDecimal.ZERO : discount.discountAmount();
        Booking booking = new Booking();
        booking.setBookingRef("BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        booking.setUser(hold.getUser());
        booking.setShow(hold.getShow());
        booking.setStatus(Booking.Status.PENDING);
        booking.setSubtotal(subtotal);
        booking.setDiscountAmount(discountAmount);
        booking.setTotalAmount(subtotal.subtract(discountAmount));
        if (discount != null) {
            booking.setDiscountCode(discount.code());
        }
        for (ShowSeat seat : seats) {
            BookingSeat bookingSeat = new BookingSeat();
            bookingSeat.setBooking(booking);
            bookingSeat.setShowSeat(seat);
            bookingSeat.setPrice(seat.getPrice());
            booking.getSeats().add(bookingSeat);
        }
        return booking;
    }

    /**
     * Charges the gateway and records the payment. On failure the thrown exception rolls back
     * the transaction (including this payment row) — acceptable here because the gateway is an
     * in-process mock; a real integration would record failed attempts in a separate transaction
     * and confirm asynchronously via gateway callback.
     */
    private Payment takePayment(Booking booking) {
        PaymentResult result = paymentGateway.charge(booking.getTotalAmount(), booking.getBookingRef());
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalAmount());
        payment.setStatus(result.success() ? Payment.Status.SUCCESS : Payment.Status.FAILED);
        payment.setTransactionRef(result.transactionRef());
        paymentRepository.save(payment);
        if (!result.success()) {
            throw ApiException.badRequest("Payment failed");
        }
        return payment;
    }

    private BookingResponse toResponse(Booking booking, String paymentStatus) {
        List<HeldSeat> seats = booking.getSeats().stream()
                .map(bs -> new HeldSeat(bs.getShowSeat().getId(), bs.getShowSeat().getSeat().label(), bs.getPrice()))
                .toList();
        return new BookingResponse(booking.getBookingRef(), booking.getShow().getId(),
                booking.getShow().getMovie().getTitle(), booking.getStatus().name(), seats,
                booking.getSubtotal(), booking.getDiscountAmount(), booking.getTotalAmount(),
                paymentStatus, booking.getCreatedAt());
    }
}
