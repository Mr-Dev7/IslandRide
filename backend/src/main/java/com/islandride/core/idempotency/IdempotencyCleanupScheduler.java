package com.islandride.core.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Periodically purges expired idempotency records to bound table growth.
 */
@Component
public class IdempotencyCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupScheduler.class);

    private final IdempotencyRecordRepository repository;

    public IdempotencyCleanupScheduler(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${islandride.idempotency.cleanup-interval-ms:3600000}")
    @Transactional
    public int cleanupExpiredRecords() {
        int deleted = repository.deleteExpiredBefore(Instant.now());
        if (deleted > 0) {
            log.debug("Purged {} expired idempotency records.", deleted);
        }
        return deleted;
    }
}

