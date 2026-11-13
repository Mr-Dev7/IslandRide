package com.islandride.paymentfinance.gateway;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "islandride.payment")
public class PaymentGatewayProperties implements InitializingBean {

    private String provider = "mock";
    private boolean liveEnabled = false;
    private int orderExpiryMinutes = 15;
    private RazorpayProperties razorpay = new RazorpayProperties();
    private MockProperties mock = new MockProperties();

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    /**
     * Validates payment configuration.
     * If live payments are explicitly enabled for the Razorpay provider,
     * required credentials (key-id, key-secret) must be provided.
     */
    public void validate() {
        if (liveEnabled && "razorpay".equalsIgnoreCase(provider)) {
            if (razorpay == null
                    || razorpay.getKeyId() == null || razorpay.getKeyId().isBlank()
                    || razorpay.getKeySecret() == null || razorpay.getKeySecret().isBlank()) {
                throw new IllegalStateException(
                        "Payment configuration error: Live Razorpay payments are enabled, but required credentials (key-id, key-secret) are missing."
                );
            }
        }
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isLiveEnabled() {
        return liveEnabled;
    }

    public void setLiveEnabled(boolean liveEnabled) {
        this.liveEnabled = liveEnabled;
    }

    public int getOrderExpiryMinutes() {
        return orderExpiryMinutes;
    }

    public void setOrderExpiryMinutes(int orderExpiryMinutes) {
        this.orderExpiryMinutes = orderExpiryMinutes;
    }

    public RazorpayProperties getRazorpay() {
        return razorpay;
    }

    public void setRazorpay(RazorpayProperties razorpay) {
        this.razorpay = razorpay;
    }

    public MockProperties getMock() {
        return mock;
    }

    public void setMock(MockProperties mock) {
        this.mock = mock;
    }

    public static class RazorpayProperties {
        private String baseUrl = "https://api.razorpay.com/v1";
        private String keyId = "";
        private String keySecret = "";
        private String webhookSecret = "";
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 15;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getKeySecret() {
            return keySecret;
        }

        public void setKeySecret(String keySecret) {
            this.keySecret = keySecret;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getReadTimeoutSeconds() {
            return readTimeoutSeconds;
        }

        public void setReadTimeoutSeconds(int readTimeoutSeconds) {
            this.readTimeoutSeconds = readTimeoutSeconds;
        }
    }

    public static class MockProperties {
        private String keySecret = "mock-islandride-key-secret";
        private String webhookSecret = "mock-islandride-webhook-secret";

        public String getKeySecret() {
            return keySecret;
        }

        public void setKeySecret(String keySecret) {
            this.keySecret = keySecret;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }
    }
}

