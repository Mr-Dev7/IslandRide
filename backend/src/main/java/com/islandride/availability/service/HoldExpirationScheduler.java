package com.islandride.availability.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HoldExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoldExpirationScheduler.class);

    private final InventoryHoldService inventoryHoldService;

    public HoldExpirationScheduler(InventoryHoldService inventoryHoldService) {
        this.inventoryHoldService = inventoryHoldService;
    }

    @Scheduled(fixedDelayString = "${islandride.holds.cleanup-interval-ms:60000}")
    public void cleanupExpiredHolds() {
        int expiredCount = inventoryHoldService.expireStaleHolds();
        if (expiredCount > 0) {
            log.info("Expired {} stale inventory hold(s).", expiredCount);
        }
    }
}

