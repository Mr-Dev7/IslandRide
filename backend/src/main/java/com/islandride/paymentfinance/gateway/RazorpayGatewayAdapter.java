package com.islandride.paymentfinance.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.islandride.core.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Production-ready Razorpay Payment Gateway Adapter using Spring 6 {@link RestClient}.
 * Connects securely to the Razorpay REST API using HTTP Basic Auth over TLS.
 * Live calls are gated behind {@code properties.isLiveEnabled()} to prevent accidental live execution.
 */
@Component
public class RazorpayGatewayAdapter implements PaymentGatewayAdapter {

    private static final Logger log = LoggerFactory.getLogger(RazorpayGatewayAdapter.class);
    public static final String PROVIDER_CODE = "razorpay";

    private final PaymentGatewayProperties properties;
    private final ObjectMapper objectMapper;
    private volatile RestClient restClient;
    private final RestClient.Builder customClientBuilder;

    @Autowired(required = false)
    private com.islandride.pricing.service.BookingPolicyService bookingPolicyService;

    @Autowired
    public RazorpayGatewayAdapter(PaymentGatewayProperties properties) {
        this(properties, new ObjectMapper().findAndRegisterModules(), null);
    }

    public RazorpayGatewayAdapter(PaymentGatewayProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules(), null);
    }

    public RazorpayGatewayAdapter(PaymentGatewayProperties properties,
                                  ObjectMapper objectMapper,
                                  RestClient.Builder customClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
        this.customClientBuilder = customClientBuilder;
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
        if (request == null || request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid order amount for Razorpay order creation.");
        }

        long amountInPaise = request.amount().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        String currency = (request.currency() != null && !request.currency().isBlank())
                ? request.currency().trim().toUpperCase()
                : "INR";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", amountInPaise);
        payload.put("currency", currency);
        if (request.receiptReference() != null && !request.receiptReference().isBlank()) {
            payload.put("receipt", request.receiptReference().trim());
        }
        if (request.notes() != null && !request.notes().isEmpty()) {
            payload.put("notes", request.notes());
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(payload);
            String rawResponse = getOrCreateRestClient().post()
                    .uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        String errBody = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        String errMsg = extractErrorMessage(errBody, resp.getStatusCode().value());
                        log.error("Razorpay order creation failed [{}]: {}", resp.getStatusCode(), errMsg);
                        throw new BusinessRuleException("Razorpay order creation rejected: " + errMsg);
                    })
                    .body(String.class);

            JsonNode root = objectMapper.readTree(rawResponse);
            String orderId = root.path("id").asText();
            String status = root.path("status").asText("created");
            long createdAtSeconds = root.path("created_at").asLong(Instant.now().getEpochSecond());
            Instant expiresAt = Instant.ofEpochSecond(createdAtSeconds).plus(Duration.ofMinutes(properties.getOrderExpiryMinutes()));

            return new GatewayOrderResponse(
                    PROVIDER_CODE,
                    orderId,
                    request.amount(),
                    currency,
                    status,
                    expiresAt,
                    rawResponse
            );
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            String errMsg = extractErrorMessage(ex.getResponseBodyAsString(), ex.getStatusCode().value());
            throw new BusinessRuleException("Razorpay API error: " + errMsg, ex);
        } catch (Exception ex) {
            log.error("Failed to execute Razorpay createOrder: {}", ex.getMessage(), ex);
            throw new BusinessRuleException("Payment gateway communication failure during order creation: " + ex.getMessage(), ex);
        }
    }

    @Override
    public GatewayPaymentStatusResponse fetchPaymentStatus(String gatewayPaymentId) {
        requireLiveEnabled();
        if (gatewayPaymentId == null || gatewayPaymentId.isBlank()) {
            throw new IllegalArgumentException("Gateway payment ID is required.");
        }

        try {
            String rawResponse = getOrCreateRestClient().get()
                    .uri("/payments/{paymentId}", gatewayPaymentId.trim())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        String errBody = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        String errMsg = extractErrorMessage(errBody, resp.getStatusCode().value());
                        log.error("Razorpay fetch payment status failed [{}]: {}", resp.getStatusCode(), errMsg);
                        throw new BusinessRuleException("Razorpay payment query failed: " + errMsg);
                    })
                    .body(String.class);

            JsonNode root = objectMapper.readTree(rawResponse);
            String orderId = root.path("order_id").asText(null);
            String status = root.path("status").asText("unknown");
            long amountPaise = root.path("amount").asLong(0);
            BigDecimal amount = BigDecimal.valueOf(amountPaise, 2).setScale(2, RoundingMode.HALF_UP);
            String currency = root.path("currency").asText("INR");
            long createdAtSeconds = root.path("created_at").asLong(Instant.now().getEpochSecond());
            Instant capturedAt = "captured".equalsIgnoreCase(status) ? Instant.ofEpochSecond(createdAtSeconds) : null;

            return new GatewayPaymentStatusResponse(
                    PROVIDER_CODE,
                    orderId,
                    gatewayPaymentId.trim(),
                    amount,
                    currency,
                    status,
                    capturedAt,
                    rawResponse
            );
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to query Razorpay payment status for {}: {}", gatewayPaymentId, ex.getMessage(), ex);
            throw new BusinessRuleException("Payment gateway status query failure: " + ex.getMessage(), ex);
        }
    }

    @Override
    public GatewayRefundResponse initiateRefund(GatewayRefundRequest request) {
        requireLiveEnabled();
        if (request == null || request.gatewayPaymentId() == null || request.gatewayPaymentId().isBlank()) {
            throw new IllegalArgumentException("Gateway payment ID is required for refund initiation.");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid refund amount.");
        }

        long amountInPaise = request.amount().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", amountInPaise);
        if (request.reason() != null && !request.reason().isBlank()) {
            payload.put("notes", Map.of("reason", request.reason().trim()));
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(payload);
            String rawResponse = getOrCreateRestClient().post()
                    .uri("/payments/{paymentId}/refund", request.gatewayPaymentId().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        String errBody = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        String errMsg = extractErrorMessage(errBody, resp.getStatusCode().value());
                        log.error("Razorpay refund initiation failed [{}]: {}", resp.getStatusCode(), errMsg);
                        throw new BusinessRuleException("Razorpay refund rejected: " + errMsg);
                    })
                    .body(String.class);

            JsonNode root = objectMapper.readTree(rawResponse);
            String refundId = root.path("id").asText();
            String status = root.path("status").asText("processed");
            String currency = root.path("currency").asText("INR");

            return new GatewayRefundResponse(
                    PROVIDER_CODE,
                    refundId,
                    request.gatewayPaymentId().trim(),
                    request.amount(),
                    currency,
                    status,
                    rawResponse
            );
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to initiate Razorpay refund for payment {}: {}", request.gatewayPaymentId(), ex.getMessage(), ex);
            throw new BusinessRuleException("Payment gateway refund failure: " + ex.getMessage(), ex);
        }
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
                    RestClient.Builder builder = this.customClientBuilder != null
                            ? this.customClientBuilder
                            : RestClient.builder();

                    if (this.customClientBuilder == null) {
                        int timeoutSec = (this.bookingPolicyService != null)
                                ? this.bookingPolicyService.resolvePaymentGatewayHttpTimeoutSeconds()
                                : properties.getRazorpay().getConnectTimeoutSeconds();
                        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                        factory.setConnectTimeout(Duration.ofSeconds(timeoutSec));
                        factory.setReadTimeout(Duration.ofSeconds(Math.max(timeoutSec, properties.getRazorpay().getReadTimeoutSeconds())));
                        builder.requestFactory(factory);
                    }

                    String credentials = properties.getRazorpay().getKeyId() + ":" + properties.getRazorpay().getKeySecret();
                    String basicAuth = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

                    client = builder
                            .baseUrl(properties.getRazorpay().getBaseUrl())
                            .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth)
                            .build();

                    this.restClient = client;
                }
            }
        }
        return client;
    }

    private void requireLiveEnabled() {
        if (!properties.isLiveEnabled()) {
            throw new BusinessRuleException(
                    "Live Razorpay gateway operations are disabled. Ensure islandride.payment.live-enabled=true to enable live gateway interactions."
            );
        }
    }

    private String extractErrorMessage(String responseBody, int statusCode) {
        if (responseBody == null || responseBody.isBlank()) {
            return "HTTP " + statusCode;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode err = root.path("error");
            if (!err.isMissingNode()) {
                String desc = err.path("description").asText();
                String code = err.path("code").asText();
                return code + ": " + desc;
            }
        } catch (JsonProcessingException ignored) {
            // fallback
        }
        return responseBody;
    }
}
