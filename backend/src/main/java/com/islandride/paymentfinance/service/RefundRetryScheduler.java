package com.islandride.paymentfinance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "islandride.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class RefundRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefundRetryScheduler.class);

    private final RefundOrchestrationService refundOrchestrationService;

    public RefundRetryScheduler(RefundOrchestrationService refundOrchestrationService) {
        this.refundOrchestrationService = refundOrchestrationService;
    }

    @Scheduled(fixedDelayString = "${islandride.scheduling.refund-retry-ms:120000}")
    public void runRefundRetrySweep() {
        try {
            int retried = refundOrchestrationService.retryDueRefunds();
            if (retried > 0) {
                log.info("Scheduled refund retry sweep processed {} refund(s).", retried);
            }
        } catch (Exception ex) {
            log.error("Error during scheduled refund retry sweep: {}", ex.getMessage(), ex);
        }
    }
}
