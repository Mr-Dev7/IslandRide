package com.islandride.booking.domain;

/**
 * Payment lifecycle summary kept separate from operational {@link BookingStatus}.
 */
public enum PaymentSummaryStatus {
    UNPAID,
    ORDER_CREATED,
    PARTIALLY_PAID,
    PAID_IN_FULL,
    FAILED,
    REFUND_PENDING,
    PARTIALLY_REFUNDED,
    REFUNDED
}

