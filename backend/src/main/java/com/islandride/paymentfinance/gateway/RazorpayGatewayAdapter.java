package com.islandride.paymentfinance.gateway;

import com.islandride.core.exception.BusinessRuleException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Razorpay gateway scaffold for Stage 6A. Live network calls remain explicitly
 * disabled until {@code islandride.payment.live-enabled=true} is authorized in later stages.
 */
@Component
public class RazorpayGatewayAdapter implements PaymentGatewayAdapter {

    public static final String PROVIDER_CODE = "razorpay";

    private final PaymentGatewayProperties properties;
    private volatile RestClient restClient;

    public RazorpayGatewayAdapter(PaymentGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public boolean isLiveEnabled() {
        return properties.isLiveEnabled();
    }

    @Override
    public GatewayOrderResponse createOrder(CreateGatewayOrderRequest request) {
        requireLiveEnabled();
        throw new BusinessRuleException("Razorpay live order creation is not activated in Stage 6A.");
    }

    @Override
    public GatewayPaymentStatusResponse fetchPaymentStatus(String gatewayPaymentId) {
        requireLiveEnabled();
        throw new BusinessRuleException("Razorpay live payment status fetch is not activated in Stage 6A.");
    }

    @Override
    public GatewayRefundResponse initiateRefund(GatewayRefundRequest request) {
        requireLiveEnabled();
        throw new BusinessRuleException("Razorpay live refund execution is not activated in Stage 6A.");
    }

    @Override
    public boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
        return RazorpaySignatureVerifier.verifyCheckoutSignature(
                orderId,
                paymentId,
                signature,
                properties.getRazorpay().getKeySecret()
        );
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signature) {
        return RazorpaySignatureVerifier.verifyWebhookSignature(
                rawPayload,
                signature,
                properties.getRazorpay().getWebhookSecret()
        );
    }

    protected RestClient getOrCreateRestClient() {
        RestClient client = this.restClient;
        if (client == null) {
            synchronized (this) {
                client = this.restClient;
                if (client == null) {
                    client = RestClient.builder()
                            .requestFactory(new SimpleClientHttpRequestFactory())
                            .baseUrl(properties.getRazorpay().getBaseUrl())
                            .build();
                    this.restClient = client;
                }
            }
        }
        return client;
    }

    private void requireLiveEnabled() {
        if (!properties.isLiveEnabled()) {
            throw new BusinessRuleException("Live Razorpay gateway operations are disabled in Stage 6A.");
        }
    }
}
