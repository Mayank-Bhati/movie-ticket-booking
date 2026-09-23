package com.mayankbhati.movietickets.booking.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mayankbhati.movietickets.booking.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    Payment findByBookingId(String bookingId);
}
