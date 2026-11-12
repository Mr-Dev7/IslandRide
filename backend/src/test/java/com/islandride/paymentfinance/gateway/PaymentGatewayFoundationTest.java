package com.islandride.paymentfinance.gateway;

import com.islandride.core.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentGatewayFoundationTest {

    @Test
    void razorpayAdapter_blocksLiveNetworkCallsWhenLiveDisabledAndVerifiesSignatures() {
        PaymentGatewayProperties props = new PaymentGatewayProperties();
        props.setProvider("mock");
        props.setLiveEnabled(false);
        props.getRazorpay().setKeySecret("rzp_test_secret_123");
        props.getRazorpay().setWebhookSecret("rzp_wh_secret_456");

        RazorpayGatewayAdapter razorpay = new RazorpayGatewayAdapter(props);
        assertThat(razorpay.isLiveEnabled()).isFalse();

        assertThatThrownBy(() -> razorpay.createOrder(new CreateGatewayOrderRequest(
                "IR-1001", new BigDecimal("1000.00"), "INR", "idem-1", Map.of())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("disabled");

        assertThatThrownBy(() -> razorpay.fetchPaymentStatus("pay_123"))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> razorpay.initiateRefund(new GatewayRefundRequest(
                "pay_123", new BigDecimal("500.00"), "INR", "CANCEL", "idem-2", Map.of())))
                .isInstanceOf(BusinessRuleException.class);

        String validCheckoutSig = RazorpaySignatureVerifier.computeHmacHex(
                "order_ABC|pay_XYZ",
                "rzp_test_secret_123"
        );
        assertThat(razorpay.verifyCheckoutSignature("order_ABC", "pay_XYZ", validCheckoutSig)).isTrue();
        assertThat(razorpay.verifyCheckoutSignature("order_ABC", "pay_TAMPERED", validCheckoutSig)).isFalse();

        String rawPayload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_XYZ\"}}}}";
        String validWebhookSig = RazorpaySignatureVerifier.computeHmacHex(rawPayload, "rzp_wh_secret_456");
        assertThat(razorpay.verifyWebhookSignature(rawPayload, validWebhookSig)).isTrue();
        assertThat(razorpay.verifyWebhookSignature(rawPayload + " ", validWebhookSig)).isFalse();
    }

    @Test
    void mockAdapter_generatesDeterministicOrdersRefundsAndValidSignatures() {
        PaymentGatewayProperties props = new PaymentGatewayProperties();
        props.setProvider("mock");
        MockGatewayAdapter mock = new MockGatewayAdapter(props);
        RazorpayGatewayAdapter razorpay = new RazorpayGatewayAdapter(props);
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(props, List.of(mock, razorpay));

        assertThat(registry.getConfiguredAdapter().providerCode()).isEqualTo("mock");

        GatewayOrderResponse order1 = mock.createOrder(new CreateGatewayOrderRequest(
                "IR-1", new BigDecimal("750.00"), "INR", "same-key", Map.of()));
        GatewayOrderResponse order2 = mock.createOrder(new CreateGatewayOrderRequest(
                "IR-1", new BigDecimal("750.00"), "INR", "same-key", Map.of()));

        assertThat(order1.gatewayOrderId()).startsWith("order_mock_").isEqualTo(order2.gatewayOrderId());
        assertThat(mock.initiateRefund(new GatewayRefundRequest(
                "pay_mock_1", new BigDecimal("750.00"), "INR", "CANCEL", "ref-1", Map.of()))
                .gatewayRefundId()).startsWith("rfnd_mock_");

        String sig = RazorpaySignatureVerifier.computeHmacHex(
                order1.gatewayOrderId() + "|pay_mock_9",
                props.getMock().getKeySecret()
        );
        assertThat(mock.verifyCheckoutSignature(order1.gatewayOrderId(), "pay_mock_9", sig)).isTrue();
    }

    @Test
    void registry_failsFastWhenProviderBlankOrUnregistered() {
        PaymentGatewayProperties props = new PaymentGatewayProperties();
        MockGatewayAdapter mock = new MockGatewayAdapter(props);
        RazorpayGatewayAdapter razorpay = new RazorpayGatewayAdapter(props);
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(props, List.of(mock, razorpay));

        props.setProvider("unknown_gw");
        assertThatThrownBy(registry::getConfiguredAdapter)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No payment gateway adapter is registered");

        props.setProvider("   ");
        assertThatThrownBy(registry::getConfiguredAdapter)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must be configured");

        props.setProvider(null);
        assertThatThrownBy(registry::getConfiguredAdapter)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must be configured");

        assertThatThrownBy(() -> registry.getAdapter("stripe"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Unsupported payment gateway provider");

        props.setProvider("mock");
        assertThat(registry.getAdapter("   ").providerCode()).isEqualTo("mock");
        assertThat(registry.getAdapter("RAZORPAY").providerCode()).isEqualTo("razorpay");
    }
}

