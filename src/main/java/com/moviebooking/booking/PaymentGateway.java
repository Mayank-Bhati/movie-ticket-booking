package com.moviebooking.booking;

import java.math.BigDecimal;

/** Abstraction over a payment provider so the booking flow can be tested with a stub. */
public interface PaymentGateway {

    PaymentResult charge(BigDecimal amount, String reference);

    record PaymentResult(boolean success, String transactionRef) {
    }
}
