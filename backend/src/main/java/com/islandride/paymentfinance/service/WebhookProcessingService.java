package com.islandride.paymentfinance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.islandride.administration.service.AuditLogService;
import com.islandride.paymentfinance.domain.Payment;
import com.islandride.paymentfinance.domain.PaymentWebhookEvent;
import com.islandride.paymentfinance.domain.WebhookProcessingStatus;
import com.islandride.paymentfinance.gateway.PaymentGatewayAdapter;
import com.islandride.paymentfinance.gateway.PaymentGatewayRegistry;
import com.islandride.paymentfinance.repository.PaymentRepository;
import com.islandride.paymentfinance.repository.PaymentWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class WebhookProcessingService {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessingService.class);

    private final PaymentWebhookEventRepository webhookRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentOrchestrationService paymentOrchestrationService;
    private final RefundOrchestrationService refundOrchestrationService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public WebhookProcessingService(PaymentWebhookEventRepository webhookRepository,
                                    PaymentRepository paymentRepository,
                                    PaymentGatewayRegistry gatewayRegistry,
                                    PaymentOrchestrationService paymentOrchestrationService,
                                    RefundOrchestrationService refundOrchestrationService,
                                    AuditLogService auditLogService) {
        this.webhookRepository = webhookRepository;
        this.paymentRepository = paymentRepository;
        this.gatewayRegistry = gatewayRegistry;
        this.paymentOrchestrationService = paymentOrchestrationService;
        this.refundOrchestrationService = refundOrchestrationService;
        this.auditLogService = auditLogService;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public record WebhookHandleOutcome(boolean accepted, boolean duplicate, boolean signatureValid, String message) {
    }

    @Transactional
    public WebhookHandleOutcome processRawWebhook(String provider,
                                                  String verbatimRawPayload,
                                                  String signatureHeader,
                                                  String headerEventId) {
        String cleanProvider = provider != null ? provider.trim().toLowerCase(Locale.ROOT) : "";
        PaymentGatewayAdapter adapter = gatewayRegistry.getAdapter(cleanProvider);
        String payloadToCheck = verbatimRawPayload != null ? verbatimRawPayload : "";

        // Mandatory raw-body HMAC-SHA256 verification BEFORE trusting or parsing JSON fields
        boolean sigValid = adapter.verifyWebhookSignature(payloadToCheck, signatureHeader);
        if (!sigValid) {
            PaymentWebhookEvent rejected = new PaymentWebhookEvent();
            rejected.setGatewayProvider(cleanProvider);
            rejected.setGatewayEventId("sigfail:" + UUID.randomUUID().toString().replace("-", ""));
            rejected.setEventType("SIGNATURE_REJECTED");
            rejected.setSignatureVerified(false);
            rejected.setProcessingStatus(WebhookProcessingStatus.FAILED);
            rejected.setPayloadJson(payloadToCheck.isBlank() ? "{}" : payloadToCheck);
            rejected.setErrorMessage("Invalid HMAC-SHA256 webhook signature");
            rejected.setProcessedAt(Instant.now());
            webhookRepository.save(rejected);
            auditLogService.record(
                    null,
                    "WEBHOOK_SIGNATURE_REJECTED",
                    "PaymentWebhookEvent",
                    rejected.getPublicId(),
                    null,
                    Map.of("provider", cleanProvider)
            );
            throw new SecurityException("Invalid webhook signature for provider " + cleanProvider);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(payloadToCheck);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Malformed webhook JSON payload: " + ex.getMessage());
        }

        String eventType = textOrNull(root, "event");
        if (eventType == null) {
            eventType = "unknown";
        }
        String eventId = firstNonBlank(
                headerEventId,
                textOrNull(root, "id"),
                "sha256_" + sha256Hex(payloadToCheck).substring(0, 32)
        );

        PaymentWebhookEvent existing = webhookRepository
                .findByGatewayProviderAndGatewayEventId(cleanProvider, eventId)
                .orElse(null);
        if (existing != null) {
            return new WebhookHandleOutcome(true, true, true, "Duplicate webhook event safely ignored.");
        }

        PaymentWebhookEvent eventRecord = new PaymentWebhookEvent();
        eventRecord.setGatewayProvider(cleanProvider);
        eventRecord.setGatewayEventId(eventId);
        eventRecord.setEventType(eventType);
        eventRecord.setSignatureVerified(true);
        eventRecord.setProcessingStatus(WebhookProcessingStatus.RECEIVED);
        eventRecord.setPayloadJson(payloadToCheck);

        try {
            eventRecord = webhookRepository.saveAndFlush(eventRecord);
        } catch (DataIntegrityViolationException concurrentDup) {
            return new WebhookHandleOutcome(true, true, true, "Concurrent duplicate webhook safely ignored.");
        }

        Instant now = Instant.now();
        try {
            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
            JsonNode refundEntity = root.path("payload").path("refund").path("entity");
            String orderId = textOrNull(paymentEntity, "order_id");
            String gatewayPaymentId = textOrNull(paymentEntity, "id");
            String errorDesc = firstNonBlank(textOrNull(paymentEntity, "error_description"), "Gateway reported payment failure");
            String gatewayRefundId = textOrNull(refundEntity, "id");

            Payment matchedPayment = findPayment(orderId, gatewayPaymentId);
            if (matchedPayment != null) {
                eventRecord.setPayment(matchedPayment);
                eventRecord.setBooking(matchedPayment.getBooking());
            }

            switch (eventType.toLowerCase(Locale.ROOT)) {
                case "payment.captured", "order.paid" ->
                        paymentOrchestrationService.processWebhookCapture(cleanProvider, orderId, gatewayPaymentId, now);
                case "payment.failed" ->
                        paymentOrchestrationService.processWebhookFailure(cleanProvider, orderId, gatewayPaymentId, errorDesc, now);
                case "refund.processed" ->
                        refundOrchestrationService.handleRefundWebhook(gatewayRefundId, true, null, now);
                case "refund.failed" ->
                        refundOrchestrationService.handleRefundWebhook(gatewayRefundId, false, errorDesc, now);
                default -> {
                    eventRecord.setProcessingStatus(WebhookProcessingStatus.IGNORED);
                    eventRecord.setProcessedAt(now);
                    webhookRepository.save(eventRecord);
                    return new WebhookHandleOutcome(true, false, true, "Unhandled event type recorded and ignored: " + eventType);
                }
            }

            eventRecord.setProcessingStatus(WebhookProcessingStatus.PROCESSED);
            eventRecord.setProcessedAt(Instant.now());
            webhookRepository.save(eventRecord);
            return new WebhookHandleOutcome(true, false, true, "Webhook processed: " + eventType);
        } catch (RuntimeException ex) {
            log.error("Failed processing verified webhook {} ({}): {}", eventId, eventType, ex.getMessage(), ex);
            eventRecord.setProcessingStatus(WebhookProcessingStatus.FAILED);
            eventRecord.setErrorMessage(ex.getMessage());
            eventRecord.setProcessedAt(Instant.now());
            webhookRepository.save(eventRecord);
            throw ex;
        }
    }

    private Payment findPayment(String orderId, String paymentId) {
        if (orderId != null) {
            Payment byOrder = paymentRepository.findByGatewayOrderId(orderId).orElse(null);
            if (byOrder != null) {
                return byOrder;
            }
        }
        if (paymentId != null) {
            return paymentRepository.findByGatewayPaymentId(paymentId).orElse(null);
        }
        return null;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) {
            return null;
        }
        String text = val.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}
