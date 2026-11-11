package com.islandride.booking.domain;

/**
 * Operational exception status tracked independently from {@link BookingStatus}
 * when a late successful payment cannot be fulfilled due to a conflicting reservation.
 */
public enum OverbookingExceptionStatus {
    NONE,
    DETECTED,
    REFUND_INITIATED,
    RESOLVED
}

