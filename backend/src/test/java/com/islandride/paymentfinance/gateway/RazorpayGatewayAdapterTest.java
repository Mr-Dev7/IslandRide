package com.islandride.paymentfinance.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.islandride.core.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RazorpayGatewayAdapterTest {

    private PaymentGatewayProperties properties;
    private ObjectMapper objectMapper;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private RazorpayGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new PaymentGatewayProperties();
        properties.setProvider("razorpay");
        properties.setLiveEnabled(true);
        properties.getRazorpay().setBaseUrl("https://api.razorpay.com/v1");
        properties.getRazorpay().setKeyId("rzp_test_123456");
        properties.getRazorpay().setKeySecret("secret_test_abcdef");
        properties.getRazorpay().setWebhookSecret("webhook_secret_9999");

        objectMapper = new ObjectMapper();
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();

        adapter = new RazorpayGatewayAdapter(properties, objectMapper, restClientBuilder);
    }

    @Test
    void createOrder_whenLiveEnabled_submitsBasicAuthAndParsesOrderResponse() {
        String mockResponse = """
                {
                    "id": "order_test_778899",
                    "entity": "order",
                    "amount": 250000,
                    "currency": "INR",
                    "receipt": "rcpt_booking_001",
                    "status": "created",
                    "created_at": 1700000000
                }
                """;

        mockServer.expect(requestTo("https://api.razorpay.com/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.amount").value(250000))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.receipt").value("rcpt_booking_001"))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        GatewayOrderResponse response = adapter.createOrder(new CreateGatewayOrderRequest(
                "rcpt_booking_001",
                new BigDecimal("2500.00"),
                "INR",
                "idemp_ord_1",
                Map.of("bookingId", "IR-1234")
        ));

        mockServer.verify();
        assertThat(response).isNotNull();
        assertThat(response.gatewayOrderId()).isEqualTo("order_test_778899");
        assertThat(response.status()).isEqualTo("created");
        assertThat(response.providerCode()).isEqualTo("razorpay");
        assertThat(response.amount()).isEqualByComparingTo("2500.00");
    }

    @Test
    void createOrder_whenLiveDisabled_throwsBusinessRuleExceptionWithoutHttp() {
        properties.setLiveEnabled(false);

        assertThatThrownBy(() -> adapter.createOrder(new CreateGatewayOrderRequest(
                "rcpt_002",
                new BigDecimal("1000.00"),
                "INR",
                null,
                Map.of()
        )))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Live Razorpay gateway operations are disabled");
    }

    @Test
    void createOrder_whenGatewayReturnsHttp400_extractsRazorpayErrorMessage() {
        String errorJson = """
                {
                    "error": {
                        "code": "BAD_REQUEST_ERROR",
                        "description": "Order amount is less than minimum allowed amount"
                    }
                }
                """;

        mockServer.expect(requestTo("https://api.razorpay.com/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body(errorJson).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.createOrder(new CreateGatewayOrderRequest(
                "rcpt_003",
                new BigDecimal("0.50"),
                "INR",
                null,
                Map.of()
        )))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("BAD_REQUEST_ERROR: Order amount is less than minimum allowed amount");

        mockServer.verify();
    }

    @Test
    void fetchPaymentStatus_whenCaptured_returnsCapturedStatus() {
        String paymentJson = """
                {
                    "id": "pay_test_998877",
                    "entity": "payment",
                    "amount": 250000,
                    "currency": "INR",
                    "status": "captured",
                    "order_id": "order_test_778899",
                    "created_at": 1700000000
                }
                """;

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_test_998877"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(paymentJson, MediaType.APPLICATION_JSON));

        GatewayPaymentStatusResponse response = adapter.fetchPaymentStatus("pay_test_998877");

        mockServer.verify();
        assertThat(response.gatewayPaymentId()).isEqualTo("pay_test_998877");
        assertThat(response.gatewayOrderId()).isEqualTo("order_test_778899");
        assertThat(response.status()).isEqualTo("captured");
        assertThat(response.amount()).isEqualByComparingTo("2500.00");
    }

    @Test
    void initiateRefund_whenSuccessful_submitsAndReturnsProcessedRefund() {
        String refundJson = """
                {
                    "id": "rfnd_test_112233",
                    "entity": "refund",
                    "amount": 150000,
                    "currency": "INR",
                    "payment_id": "pay_test_998877",
                    "status": "processed",
                    "created_at": 1700000000
                }
                """;

        mockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_test_998877/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.amount").value(150000))
                .andRespond(withSuccess(refundJson, MediaType.APPLICATION_JSON));

        GatewayRefundResponse response = adapter.initiateRefund(new GatewayRefundRequest(
                "pay_test_998877",
                new BigDecimal("1500.00"),
                "INR",
                "Tourist requested cancellation",
                "idemp_rfnd_1",
                Map.of()
        ));

        mockServer.verify();
        assertThat(response.gatewayRefundId()).isEqualTo("rfnd_test_112233");
        assertThat(response.gatewayPaymentId()).isEqualTo("pay_test_998877");
        assertThat(response.status()).isEqualTo("processed");
        assertThat(response.amount()).isEqualByComparingTo("1500.00");
    }

    @Test
    void signatures_checkoutAndWebhook_verifiedCorrectly() {
        String orderId = "order_test_123";
        String paymentId = "pay_test_456";
        String validCheckoutSig = RazorpaySignatureVerifier.computeHmacHex(
                orderId + "|" + paymentId,
                properties.getRazorpay().getKeySecret()
        );

        assertThat(adapter.verifyCheckoutSignature(orderId, paymentId, validCheckoutSig)).isTrue();
        assertThat(adapter.verifyCheckoutSignature(orderId, paymentId, "invalid_sig")).isFalse();

        String rawPayload = "{\"event\":\"payment.captured\",\"id\":\"evt_123\"}";
        String validWebhookSig = RazorpaySignatureVerifier.computeHmacHex(
                rawPayload,
                properties.getRazorpay().getWebhookSecret()
        );

        assertThat(adapter.verifyWebhookSignature(rawPayload, validWebhookSig)).isTrue();
        assertThat(adapter.verifyWebhookSignature(rawPayload, "invalid_sig")).isFalse();
    }
}

