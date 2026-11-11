package com.islandride.booking.domain;

import java.util.EnumSet;
import java.util.Set;

public enum BookingStatus {
    PENDING_PAYMENT,
    PAYMENT_FAILED,
    AWAITING_PARTNER_APPROVAL,
    APPROVED,
    REJECTED,
    CONFIRMED,
    ACTIVE,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    NO_SHOW,
    DISPUTED;

    public static final Set<BookingStatus> NON_BLOCKING_STATUSES = EnumSet.of(
            PAYMENT_FAILED,
            REJECTED,
            CANCELLED,
            EXPIRED,
            NO_SHOW,
            COMPLETED
    );

    public boolean blocksInventory() {
        return !NON_BLOCKING_STATUSES.contains(this);
    }
}
