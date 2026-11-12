package com.islandride.paymentfinance.api;

import com.islandride.paymentfinance.dto.PaymentDtos;
import com.islandride.paymentfinance.service.PaymentOrchestrationService;
import com.islandride.paymentfinance.service.WebhookProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payment Gateway & Webhooks", description = "Inbound payment gateway webhooks and test simulation endpoints")
public class PaymentWebhookController {

    private final WebhookProcessingService webhookProcessingService;
    private final PaymentOrchestrationService paymentOrchestrationService;

    public PaymentWebhookController(WebhookProcessingService webhookProcessingService,
                                    PaymentOrchestrationService paymentOrchestrationService) {
        this.webhookProcessingService = webhookProcessingService;
        this.paymentOrchestrationService = paymentOrchestrationService;
    }

    @PostMapping("/webhooks/{provider}")
    @Operation(summary = "Inbound payment gateway webhook handler with HMAC-SHA256 signature verification")
    public ResponseEntity<WebhookProcessingService.WebhookHandleOutcome> handleGatewayWebhook(
            @PathVariable String provider,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String razorpaySigHeader,
            @RequestHeader(value = "X-Signature", required = false) String genericSigHeader,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventIdHeader,
            HttpServletRequest request) throws IOException {

        // Read verbatim raw payload bytes BEFORE Spring or Jackson parse to guarantee byte-for-byte HMAC integrity
        byte[] rawBytes = request.getInputStream().readAllBytes();
        String rawPayload = new String(rawBytes, StandardCharsets.UTF_8);
        String signature = (razorpaySigHeader != null && !razorpaySigHeader.isBlank())
                ? razorpaySigHeader
                : genericSigHeader;

        WebhookProcessingService.WebhookHandleOutcome outcome = webhookProcessingService
                .processRawWebhook(provider, rawPayload, signature, eventIdHeader);
        return ResponseEntity.ok(outcome);
    }

    @PostMapping("/mock/simulate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'FINANCE_ADMIN', 'OPERATIONS_ADMIN', 'TOURIST')")
    @Operation(summary = "Generate signed mock gateway payloads for automated testing and checkout verification")
    public ResponseEntity<PaymentDtos.MockPaymentSimulateResponse> simulateMockPayment(
            @Valid @RequestBody PaymentDtos.MockPaymentSimulateRequest request) {
        return ResponseEntity.ok(paymentOrchestrationService.buildMockSimulationPayload(request));
    }
}
