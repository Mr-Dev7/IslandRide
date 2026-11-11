package com.islandride.paymentfinance.gateway;

import java.math.BigDecimal;
import java.time.Instant;

public record GatewayPaymentStatusResponse(
        String providerCode,
        String gatewayOrderId,
        String gatewayPaymentId,
        BigDecimal amount,
        String currency,
        String status,
        Instant capturedAt,
        String rawPayload
) {
}

