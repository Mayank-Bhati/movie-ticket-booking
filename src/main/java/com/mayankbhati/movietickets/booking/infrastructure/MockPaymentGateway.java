package com.mayankbhati.movietickets.booking.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mayankbhati.movietickets.booking.application.PaymentGateway;
import com.mayankbhati.movietickets.shared.ApiException;

@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentReceipt capture(long amountCents, String currency, String paymentMethod,
                                  String idempotencyKey) {
        if ("decline".equalsIgnoreCase(paymentMethod)) {
            throw ApiException.conflict("PAYMENT_DECLINED", "The mock payment gateway declined the payment");
        }
        String stableReference = UUID.nameUUIDFromBytes(
                ("capture:" + idempotencyKey).getBytes(StandardCharsets.UTF_8)).toString();
        return new PaymentReceipt("pay_" + stableReference);
    }

    @Override
    public String refund(String providerReference, long amountCents, String idempotencyKey) {
        String stableReference = UUID.nameUUIDFromBytes(
                ("refund:" + idempotencyKey).getBytes(StandardCharsets.UTF_8)).toString();
        return "ref_" + stableReference;
    }
}

