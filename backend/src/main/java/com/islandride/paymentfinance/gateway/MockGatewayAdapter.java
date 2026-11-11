package com.islandride.paymentfinance.gateway;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
@Profile({"local", "test"})
public class MockGatewayAdapter implements PaymentGatewayAdapter {

    public static final String PROVIDER_CODE = "mock";

    private final PaymentGatewayProperties properties;

    public MockGatewayAdapter(PaymentGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public boolean isLiveEnabled() {
        return false;
    }

    @Override
    public GatewayOrderResponse createOrder(CreateGatewayOrderRequest request) {
        String seed = request.idempotencyKey() != null && !request.idempotencyKey().isBlank()
                ? request.idempotencyKey()
                : UUID.randomUUID().toString();
        String suffix = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "")
                .substring(0, 14);
        String orderId = "order_mock_" + suffix;
        Instant expiresAt = Instant.now().plusSeconds(Math.max(1, properties.getOrderExpiryMinutes()) * 60L);
        return new GatewayOrderResponse(
                PROVIDER_CODE,
                orderId,
                request.amount(),
                request.currency() != null ? request.currency() : "INR",
                "created",
                expiresAt,
                "{\"id\":\"" + orderId + "\",\"status\":\"created\",\"mock\":true}"
        );
    }

    @Override
    public GatewayPaymentStatusResponse fetchPaymentStatus(String gatewayPaymentId) {
        return new GatewayPaymentStatusResponse(
                PROVIDER_CODE,
                "order_mock_lookup",
                gatewayPaymentId,
                null,
                "INR",
                "captured",
                Instant.now(),
                "{\"id\":\"" + gatewayPaymentId + "\",\"status\":\"captured\",\"mock\":true}"
        );
    }

    @Override
    public GatewayRefundResponse initiateRefund(GatewayRefundRequest request) {
        String seed = request.idempotencyKey() != null && !request.idempotencyKey().isBlank()
                ? request.idempotencyKey()
                : request.gatewayPaymentId();
        String suffix = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "")
                .substring(0, 14);
        String refundId = "rfnd_mock_" + suffix;
        return new GatewayRefundResponse(
                PROVIDER_CODE,
                refundId,
                request.gatewayPaymentId(),
                request.amount(),
                request.currency() != null ? request.currency() : "INR",
                "processed",
                "{\"id\":\"" + refundId + "\",\"status\":\"processed\",\"mock\":true}"
        );
    }

    @Override
    public boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
        return RazorpaySignatureVerifier.verifyCheckoutSignature(
                orderId,
                paymentId,
                signature,
                properties.getMock().getKeySecret()
        );
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signature) {
        return RazorpaySignatureVerifier.verifyWebhookSignature(
                rawPayload,
                signature,
                properties.getMock().getWebhookSecret()
        );
    }
}

