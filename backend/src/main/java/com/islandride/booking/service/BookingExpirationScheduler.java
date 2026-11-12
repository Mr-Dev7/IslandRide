package com.islandride.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "islandride.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class BookingExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingExpirationScheduler.class);

    private final BookingOrchestrationService bookingOrchestrationService;

    public BookingExpirationScheduler(BookingOrchestrationService bookingOrchestrationService) {
        this.bookingOrchestrationService = bookingOrchestrationService;
    }

    @Scheduled(fixedDelayString = "${islandride.scheduling.booking-expiration-ms:60000}")
    public void runBookingExpirationSweep() {
        try {
            int expired = bookingOrchestrationService.expireStaleBookings();
            if (expired > 0) {
                log.info("Booking expiration sweep completed: {} stale booking(s) transitioned to EXPIRED.", expired);
            }
        } catch (Exception ex) {
            log.error("Error during scheduled booking expiration sweep: {}", ex.getMessage(), ex);
        }
    }
}
