package com.islandride.booking.domain;

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
    DISPUTED
}

