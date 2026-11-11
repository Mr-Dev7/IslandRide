package com.islandride.paymentfinance.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "islandride.payment")
public class PaymentGatewayProperties {

    private String provider = "mock";
    private boolean liveEnabled = false;
    private int orderExpiryMinutes = 15;
    private RazorpayProperties razorpay = new RazorpayProperties();
    private MockProperties mock = new MockProperties();

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

