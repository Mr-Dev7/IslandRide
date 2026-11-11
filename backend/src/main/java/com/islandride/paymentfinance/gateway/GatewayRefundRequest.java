package com.islandride.paymentfinance.gateway;

import java.math.BigDecimal;
import java.util.Map;

public record GatewayRefundRequest(
        String gatewayPaymentId,
        BigDecimal amount,
        String currency,
        String reason,
        String idempotencyKey,
        Map<String, String> notes
) {
}

