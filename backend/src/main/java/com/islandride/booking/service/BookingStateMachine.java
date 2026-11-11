package com.islandride.booking.service;

import com.islandride.booking.domain.Booking;
import com.islandride.booking.domain.BookingStatus;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class BookingStateMachine {

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(BookingStatus.class);
    private static final Map<BookingStatus, Map<BookingStatus, Set<BookingActorRole>>> ROLE_GUARDS = new EnumMap<>(BookingStatus.class);

    static {
        allow(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED, BookingActorRole.SYSTEM);
        allow(BookingStatus.PENDING_PAYMENT, BookingStatus.PAYMENT_FAILED, BookingActorRole.SYSTEM);
        allow(BookingStatus.PENDING_PAYMENT, BookingStatus.EXPIRED, BookingActorRole.SYSTEM);
        allow(BookingStatus.PENDING_PAYMENT, BookingStatus.CANCELLED, BookingActorRole.TOURIST, BookingActorRole.ADMIN, BookingActorRole.SYSTEM);

        allow(BookingStatus.PAYMENT_FAILED, BookingStatus.PENDING_PAYMENT, BookingActorRole.TOURIST, BookingActorRole.SYSTEM);
        allow(BookingStatus.PAYMENT_FAILED, BookingStatus.EXPIRED, BookingActorRole.SYSTEM);
        allow(BookingStatus.PAYMENT_FAILED, BookingStatus.CANCELLED, BookingActorRole.TOURIST, BookingActorRole.ADMIN);

        allow(BookingStatus.AWAITING_PARTNER_APPROVAL, BookingStatus.APPROVED, BookingActorRole.PARTNER, BookingActorRole.ADMIN);
        allow(BookingStatus.AWAITING_PARTNER_APPROVAL, BookingStatus.REJECTED, BookingActorRole.PARTNER, BookingActorRole.ADMIN);
        allow(BookingStatus.AWAITING_PARTNER_APPROVAL, BookingStatus.EXPIRED, BookingActorRole.SYSTEM);
        allow(BookingStatus.AWAITING_PARTNER_APPROVAL, BookingStatus.CANCELLED, BookingActorRole.TOURIST, BookingActorRole.ADMIN);

        allow(BookingStatus.APPROVED, BookingStatus.PENDING_PAYMENT, BookingActorRole.TOURIST, BookingActorRole.SYSTEM);
        allow(BookingStatus.APPROVED, BookingStatus.CONFIRMED, BookingActorRole.SYSTEM);
        allow(BookingStatus.APPROVED, BookingStatus.EXPIRED, BookingActorRole.SYSTEM);
        allow(BookingStatus.APPROVED, BookingStatus.CANCELLED, BookingActorRole.TOURIST, BookingActorRole.PARTNER, BookingActorRole.ADMIN);

        allow(BookingStatus.CONFIRMED, BookingStatus.ACTIVE, BookingActorRole.PARTNER, BookingActorRole.ADMIN);
        allow(BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingActorRole.TOURIST, BookingActorRole.PARTNER, BookingActorRole.ADMIN);
        allow(BookingStatus.CONFIRMED, BookingStatus.NO_SHOW, BookingActorRole.PARTNER, BookingActorRole.ADMIN);
        allow(BookingStatus.CONFIRMED, BookingStatus.DISPUTED, BookingActorRole.TOURIST, BookingActorRole.PARTNER, BookingActorRole.ADMIN);

        allow(BookingStatus.ACTIVE, BookingStatus.COMPLETED, BookingActorRole.PARTNER, BookingActorRole.ADMIN);
        allow(BookingStatus.ACTIVE, BookingStatus.DISPUTED, BookingActorRole.TOURIST, BookingActorRole.PARTNER, BookingActorRole.ADMIN);
        allow(BookingStatus.ACTIVE, BookingStatus.CANCELLED, BookingActorRole.ADMIN);

        allow(BookingStatus.DISPUTED, BookingStatus.COMPLETED, BookingActorRole.ADMIN);
        allow(BookingStatus.DISPUTED, BookingStatus.CANCELLED, BookingActorRole.ADMIN);
    }

    private static void allow(BookingStatus from, BookingStatus to, BookingActorRole... actors) {
        ALLOWED_TRANSITIONS.computeIfAbsent(from, k -> EnumSet.noneOf(BookingStatus.class)).add(to);
        ROLE_GUARDS.computeIfAbsent(from, k -> new EnumMap<>(BookingStatus.class))
                .put(to, EnumSet.of(actors[0], actors));
    }

    public boolean isTransitionAllowed(BookingStatus from, BookingStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public boolean isActorAuthorized(BookingStatus from, BookingStatus to, BookingActorRole actorRole) {
        if (actorRole == null || !isTransitionAllowed(from, to)) {
            return false;
        }
        return ROLE_GUARDS.getOrDefault(from, Map.of())
                .getOrDefault(to, Set.of())
                .contains(actorRole);
    }

    public void validateTransition(Booking booking,
                                   BookingStatus targetStatus,
                                   BookingActorRole actorRole,
                                   Instant now) {
        BookingStatus current = booking.getStatus();
        Instant effectiveNow = now != null ? now : Instant.now();

        if (!isTransitionAllowed(current, targetStatus)) {
            throw new InvalidStateTransitionException(
                    "Booking transition not permitted: " + current + " -> " + targetStatus
            );
        }
        if (!isActorAuthorized(current, targetStatus, actorRole)) {
            throw new InvalidStateTransitionException(
                    "Actor role " + actorRole + " is not permitted to transition booking from "
                            + current + " to " + targetStatus
            );
        }
        if (current == BookingStatus.AWAITING_PARTNER_APPROVAL
                && (targetStatus == BookingStatus.APPROVED || targetStatus == BookingStatus.REJECTED)
                && booking.getApprovalExpiresAt() != null
                && !effectiveNow.isBefore(booking.getApprovalExpiresAt())) {
            throw new BusinessRuleException("Partner approval deadline has already expired for booking " + booking.getPublicId());
        }
        if (current == BookingStatus.APPROVED
                && (targetStatus == BookingStatus.PENDING_PAYMENT || targetStatus == BookingStatus.CONFIRMED)
                && booking.getPaymentDeadlineAt() != null
                && !effectiveNow.isBefore(booking.getPaymentDeadlineAt())) {
            throw new BusinessRuleException("Post-approval payment window has already expired for booking " + booking.getPublicId());
        }
    }
}

