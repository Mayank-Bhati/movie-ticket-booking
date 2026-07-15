package com.moviebooking.booking;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Mock payment provider. Approves any positive charge and returns a transaction reference.
 * A real gateway integration would replace this bean without touching the booking flow.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult charge(BigDecimal amount, String reference) {
        boolean success = amount != null && amount.signum() >= 0;
        String txnRef = success ? "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase() : null;
        return new PaymentResult(success, txnRef);
    }
}
