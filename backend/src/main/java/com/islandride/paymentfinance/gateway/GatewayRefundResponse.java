package com.islandride.paymentfinance.gateway;

import java.math.BigDecimal;

public record GatewayRefundResponse(
        String providerCode,
        String gatewayRefundId,
        String gatewayPaymentId,
        BigDecimal amount,
        String currency,
        String status,
        String rawPayload
) {
}

