package com.islandride.paymentfinance.service;

import com.islandride.core.exception.InvalidStateTransitionException;
import com.islandride.paymentfinance.domain.Refund;
import com.islandride.paymentfinance.domain.RefundStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class RefundStateMachine {

    private static final Map<RefundStatus, Set<RefundStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(RefundStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(RefundStatus.PENDING, EnumSet.of(RefundStatus.PROCESSING, RefundStatus.SUCCESS, RefundStatus.FAILED));
        ALLOWED_TRANSITIONS.put(RefundStatus.PROCESSING, EnumSet.of(RefundStatus.SUCCESS, RefundStatus.FAILED, RefundStatus.RECONCILED));
        ALLOWED_TRANSITIONS.put(RefundStatus.FAILED, EnumSet.of(RefundStatus.PROCESSING));
        ALLOWED_TRANSITIONS.put(RefundStatus.SUCCESS, EnumSet.of(RefundStatus.RECONCILED));
        ALLOWED_TRANSITIONS.put(RefundStatus.RECONCILED, EnumSet.noneOf(RefundStatus.class));
    }

    public boolean isTransitionAllowed(RefundStatus from, RefundStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public Refund transition(Refund refund, RefundStatus targetStatus, String errorMessage, Instant now) {
        RefundStatus current = refund.getStatus();
        if (!isTransitionAllowed(current, targetStatus)) {
            throw new InvalidStateTransitionException(
                    "Refund transition not permitted: " + current + " -> " + targetStatus
            );
        }
        Instant effectiveNow = now != null ? now : Instant.now();
        refund.setStatus(targetStatus);
        if (targetStatus == RefundStatus.FAILED) {
            refund.setRetryCount(refund.getRetryCount() + 1);
            refund.setErrorMessage(errorMessage);
        } else if (targetStatus == RefundStatus.SUCCESS) {
            refund.setErrorMessage(null);
            refund.setNextRetryAt(null);
        } else if (targetStatus == RefundStatus.RECONCILED) {
            refund.setReconciledAt(effectiveNow);
            refund.setErrorMessage(null);
            refund.setNextRetryAt(null);
        }
        return refund;
    }
}

