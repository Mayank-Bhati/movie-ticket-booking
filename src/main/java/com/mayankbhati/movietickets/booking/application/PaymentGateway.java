package com.mayankbhati.movietickets.booking.application;

public interface PaymentGateway {
    PaymentReceipt capture(long amountCents, String currency, String paymentMethod,
                           String idempotencyKey);

    String refund(String providerReference, long amountCents, String idempotencyKey);

    record PaymentReceipt(String reference) {
    }
}

