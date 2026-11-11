package com.islandride.paymentfinance.gateway;

import java.math.BigDecimal;
import java.util.Map;

public record CreateGatewayOrderRequest(
        String receiptReference,
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        Map<String, String> notes
) {
}

