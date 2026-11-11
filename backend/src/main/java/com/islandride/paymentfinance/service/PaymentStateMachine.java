package com.islandride.paymentfinance.service;

import com.islandride.core.exception.InvalidStateTransitionException;
import com.islandride.paymentfinance.domain.Payment;
import com.islandride.paymentfinance.domain.PaymentStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PaymentStatus.INITIATED, EnumSet.of(PaymentStatus.CAPTURED, PaymentStatus.FAILED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.CAPTURED, EnumSet.of(PaymentStatus.REFUNDED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.FAILED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED_TRANSITIONS.put(PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class));
    }

    public boolean isTransitionAllowed(PaymentStatus from, PaymentStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public Payment transition(Payment payment, PaymentStatus targetStatus, String reason, Instant now) {
        PaymentStatus current = payment.getStatus();
        if (!isTransitionAllowed(current, targetStatus)) {
            throw new InvalidStateTransitionException(
                    "Payment transition not permitted: " + current + " -> " + targetStatus
            );
        }
        Instant effectiveNow = now != null ? now : Instant.now();
        payment.setStatus(targetStatus);
        if (targetStatus == PaymentStatus.CAPTURED) {
            payment.setCapturedAt(effectiveNow);
            payment.setFailedAt(null);
            payment.setFailureReason(null);
        } else if (targetStatus == PaymentStatus.FAILED) {
            payment.setFailedAt(effectiveNow);
            if (reason != null && !reason.isBlank()) {
                payment.setFailureReason(reason.length() > 255 ? reason.substring(0, 255) : reason);
            }
        }
        return payment;
    }
}

