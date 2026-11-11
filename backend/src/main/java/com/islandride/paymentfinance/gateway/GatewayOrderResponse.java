package com.islandride.paymentfinance.gateway;

import java.math.BigDecimal;
import java.time.Instant;

public record GatewayOrderResponse(
        String providerCode,
        String gatewayOrderId,
        BigDecimal amount,
        String currency,
        String status,
        Instant expiresAt,
        String rawPayload
) {
}

