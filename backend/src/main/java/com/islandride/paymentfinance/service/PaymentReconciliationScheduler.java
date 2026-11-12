package com.islandride.paymentfinance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "islandride.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationScheduler.class);

    private final PaymentOrchestrationService paymentOrchestrationService;

    public PaymentReconciliationScheduler(PaymentOrchestrationService paymentOrchestrationService) {
        this.paymentOrchestrationService = paymentOrchestrationService;
    }

    @Scheduled(fixedDelayString = "${islandride.scheduling.payment-reconciliation-ms:180000}")
    public void runPaymentReconciliationSweep() {
        try {
            int reconciled = paymentOrchestrationService.reconcileStaleInitiatedPayments();
            if (reconciled > 0) {
                log.info("Scheduled payment reconciliation sweep resolved {} stale gateway payment(s).", reconciled);
            }
        } catch (Exception ex) {
            log.error("Error during scheduled payment reconciliation sweep: {}", ex.getMessage(), ex);
        }
    }
}
