package com.islandride.paymentfinance.gateway;

public interface PaymentGatewayAdapter {

    String providerCode();

    boolean isLiveEnabled();

    GatewayOrderResponse createOrder(CreateGatewayOrderRequest request);

    GatewayPaymentStatusResponse fetchPaymentStatus(String gatewayPaymentId);

    GatewayRefundResponse initiateRefund(GatewayRefundRequest request);

    boolean verifyCheckoutSignature(String orderId, String paymentId, String signature);

    boolean verifyWebhookSignature(String rawPayload, String signature);
}

