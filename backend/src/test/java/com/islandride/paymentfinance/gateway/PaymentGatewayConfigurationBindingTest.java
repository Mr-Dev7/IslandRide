package com.islandride.paymentfinance.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentGatewayConfigurationBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PaymentGatewayProperties.class)
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class));

    @Nested
    @DisplayName("Default and Production Property Files Binding")
    class YamlFileBindingTests {

        @Test
        @DisplayName("application.yml sets provider=mock and live-enabled=false by default")
        void baseApplicationYml_defaultsToMockAndLiveDisabled() throws IOException {
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            List<PropertySource<?>> sources = loader.load(
                    "application.yml",
                    new ClassPathResource("application.yml")
            );
            assertThat(sources).isNotEmpty();
            PropertySource<?> ps = sources.get(0);

            assertThat(ps.getProperty("islandride.payment.provider"))
                    .isEqualTo("${ISLANDRIDE_PAYMENT_PROVIDER:${PAYMENT_GATEWAY_PROVIDER:mock}}");
            assertThat(ps.getProperty("islandride.payment.live-enabled"))
                    .isEqualTo("${ISLANDRIDE_PAYMENT_LIVE_ENABLED:${PAYMENT_LIVE_ENABLED:false}}");
            assertThat(ps.getProperty("islandride.payment.order-expiry-minutes"))
                    .isEqualTo("${ISLANDRIDE_PAYMENT_ORDER_EXPIRY_MINUTES:15}");
            assertThat(ps.getProperty("islandride.payment.razorpay.base-url"))
                    .isEqualTo("${RAZORPAY_BASE_URL:https://api.razorpay.com/v1}");
        }

        @Test
        @DisplayName("application-prod.yml sets provider=razorpay and live-enabled=false by default")
        void prodApplicationYml_defaultsToRazorpayAndLiveDisabledWithoutHardcodedSecrets() throws IOException {
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            List<PropertySource<?>> sources = loader.load(
                    "application-prod.yml",
                    new ClassPathResource("application-prod.yml")
            );
            assertThat(sources).isNotEmpty();
            PropertySource<?> ps = sources.get(0);

            assertThat(ps.getProperty("islandride.payment.provider"))
                    .isEqualTo("${ISLANDRIDE_PAYMENT_PROVIDER:${PAYMENT_GATEWAY_PROVIDER:razorpay}}");
            assertThat(ps.getProperty("islandride.payment.live-enabled"))
                    .isEqualTo("${ISLANDRIDE_PAYMENT_LIVE_ENABLED:${PAYMENT_LIVE_ENABLED:false}}");
            assertThat(ps.getProperty("islandride.payment.razorpay.key-id"))
                    .isEqualTo("${ISLANDRIDE_PAYMENT_RAZORPAY_KEY_ID:${RAZORPAY_KEY_ID:}}");
            assertThat(ps.getProperty("islandride.payment.razorpay.key-secret"))
                    .isEqualTo("${ISLANDRIDE_PAYMENT_RAZORPAY_KEY_SECRET:${RAZORPAY_KEY_SECRET:}}");
            assertThat(ps.getProperty("islandride.payment.razorpay.webhook-secret"))
                    .isEqualTo("${ISLANDRIDE_PAYMENT_RAZORPAY_WEBHOOK_SECRET:${RAZORPAY_WEBHOOK_SECRET:}}");
        }
    }

    @Nested
    @DisplayName("Configuration Properties Binding via Spring Runner")
    class PropertiesBindingTests {

        @Test
        @DisplayName("Default bean initialization has mock provider and liveEnabled=false")
        void defaultProperties_hasMockProviderAndLiveDisabled() {
            contextRunner.run(context -> {
                assertThat(context).hasNotFailed();
                PaymentGatewayProperties props = context.getBean(PaymentGatewayProperties.class);
                assertThat(props.getProvider()).isEqualTo("mock");
                assertThat(props.isLiveEnabled()).isFalse();
                assertThat(props.getOrderExpiryMinutes()).isEqualTo(15);
                assertThat(props.getRazorpay().getBaseUrl()).isEqualTo("https://api.razorpay.com/v1");
                assertThat(props.getRazorpay().getKeyId()).isEmpty();
                assertThat(props.getRazorpay().getKeySecret()).isEmpty();
                assertThat(props.getRazorpay().getWebhookSecret()).isEmpty();
            });
        }

        @Test
        @DisplayName("Canonical islandride.payment properties bind successfully")
        void canonicalProperties_bindSuccessfully() {
            contextRunner
                    .withPropertyValues(
                            "islandride.payment.provider=razorpay",
                            "islandride.payment.live-enabled=false",
                            "islandride.payment.order-expiry-minutes=20",
                            "islandride.payment.razorpay.base-url=https://custom.razorpay.com/v1",
                            "islandride.payment.razorpay.key-id=rzp_test_canon123",
                            "islandride.payment.razorpay.key-secret=canon_secret_456",
                            "islandride.payment.razorpay.webhook-secret=canon_wh_789",
                            "islandride.payment.razorpay.connect-timeout-seconds=8",
                            "islandride.payment.razorpay.read-timeout-seconds=12"
                    )
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        PaymentGatewayProperties props = context.getBean(PaymentGatewayProperties.class);
                        assertThat(props.getProvider()).isEqualTo("razorpay");
                        assertThat(props.isLiveEnabled()).isFalse();
                        assertThat(props.getOrderExpiryMinutes()).isEqualTo(20);
                        assertThat(props.getRazorpay().getBaseUrl()).isEqualTo("https://custom.razorpay.com/v1");
                        assertThat(props.getRazorpay().getKeyId()).isEqualTo("rzp_test_canon123");
                        assertThat(props.getRazorpay().getKeySecret()).isEqualTo("canon_secret_456");
                        assertThat(props.getRazorpay().getWebhookSecret()).isEqualTo("canon_wh_789");
                        assertThat(props.getRazorpay().getConnectTimeoutSeconds()).isEqualTo(8);
                        assertThat(props.getRazorpay().getReadTimeoutSeconds()).isEqualTo(12);
                    });
        }

        @Test
        @DisplayName("Environment variable fallback bindings work with application.yml expressions")
        void envFallbackProperties_bindSuccessfully() {
            contextRunner
                    .withPropertyValues(
                            "PAYMENT_GATEWAY_PROVIDER=razorpay",
                            "PAYMENT_LIVE_ENABLED=false",
                            "RAZORPAY_KEY_ID=rzp_env_fallback_id",
                            "RAZORPAY_KEY_SECRET=rzp_env_fallback_secret",
                            "RAZORPAY_WEBHOOK_SECRET=rzp_env_fallback_wh",
                            // Simulate YAML evaluation resolving expressions
                            "islandride.payment.provider=${ISLANDRIDE_PAYMENT_PROVIDER:${PAYMENT_GATEWAY_PROVIDER:mock}}",
                            "islandride.payment.live-enabled=${ISLANDRIDE_PAYMENT_LIVE_ENABLED:${PAYMENT_LIVE_ENABLED:false}}",
                            "islandride.payment.razorpay.key-id=${ISLANDRIDE_PAYMENT_RAZORPAY_KEY_ID:${RAZORPAY_KEY_ID:}}",
                            "islandride.payment.razorpay.key-secret=${ISLANDRIDE_PAYMENT_RAZORPAY_KEY_SECRET:${RAZORPAY_KEY_SECRET:}}",
                            "islandride.payment.razorpay.webhook-secret=${ISLANDRIDE_PAYMENT_RAZORPAY_WEBHOOK_SECRET:${RAZORPAY_WEBHOOK_SECRET:}}"
                    )
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        PaymentGatewayProperties props = context.getBean(PaymentGatewayProperties.class);
                        assertThat(props.getProvider()).isEqualTo("razorpay");
                        assertThat(props.isLiveEnabled()).isFalse();
                        assertThat(props.getRazorpay().getKeyId()).isEqualTo("rzp_env_fallback_id");
                        assertThat(props.getRazorpay().getKeySecret()).isEqualTo("rzp_env_fallback_secret");
                        assertThat(props.getRazorpay().getWebhookSecret()).isEqualTo("rzp_env_fallback_wh");
                    });
        }
    }

    @Nested
    @DisplayName("Validation and Safe Failure Tests")
    class ValidationTests {

        @Test
        @DisplayName("Validation passes when liveEnabled=false even if credentials are blank")
        void validate_passesWhenLiveDisabledWithEmptyCredentials() {
            PaymentGatewayProperties props = new PaymentGatewayProperties();
            props.setProvider("razorpay");
            props.setLiveEnabled(false);
            props.getRazorpay().setKeyId("");
            props.getRazorpay().setKeySecret("");

            props.validate();
            assertThat(props.isLiveEnabled()).isFalse();
        }

        @Test
        @DisplayName("Validation passes when provider=mock even if liveEnabled=true")
        void validate_passesWhenProviderMockEvenIfLiveEnabled() {
            PaymentGatewayProperties props = new PaymentGatewayProperties();
            props.setProvider("mock");
            props.setLiveEnabled(true);

            props.validate();
            assertThat(props.getProvider()).isEqualTo("mock");
        }

        @Test
        @DisplayName("Validation passes when liveEnabled=true and valid credentials are provided")
        void validate_passesWhenLiveEnabledWithValidCredentials() {
            PaymentGatewayProperties props = new PaymentGatewayProperties();
            props.setProvider("razorpay");
            props.setLiveEnabled(true);
            props.getRazorpay().setKeyId("rzp_live_key_valid");
            props.getRazorpay().setKeySecret("rzp_live_secret_valid");

            props.validate();
            assertThat(props.isLiveEnabled()).isTrue();
        }

        @Test
        @DisplayName("Validation throws IllegalStateException when liveEnabled=true and key-id is blank")
        void validate_failsWhenLiveEnabledAndKeyIdBlank() {
            PaymentGatewayProperties props = new PaymentGatewayProperties();
            props.setProvider("razorpay");
            props.setLiveEnabled(true);
            props.getRazorpay().setKeyId("   ");
            props.getRazorpay().setKeySecret("rzp_live_secret_valid");

            assertThatThrownBy(props::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Payment configuration error: Live Razorpay payments are enabled, but required credentials (key-id, key-secret) are missing.")
                    // Ensure secrets are never exposed in exception message
                    .hasMessageNotContaining("rzp_live_secret_valid");
        }

        @Test
        @DisplayName("Validation throws IllegalStateException when liveEnabled=true and key-secret is blank")
        void validate_failsWhenLiveEnabledAndKeySecretBlank() {
            PaymentGatewayProperties props = new PaymentGatewayProperties();
            props.setProvider("razorpay");
            props.setLiveEnabled(true);
            props.getRazorpay().setKeyId("rzp_live_key_valid");
            props.getRazorpay().setKeySecret("");

            assertThatThrownBy(props::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Payment configuration error: Live Razorpay payments are enabled, but required credentials (key-id, key-secret) are missing.")
                    .hasMessageNotContaining("rzp_live_key_valid");
        }

        @Test
        @DisplayName("Application context fails safely on startup when liveEnabled=true and credentials missing")
        void contextFailsSafely_whenLiveEnabledWithoutCredentials() {
            contextRunner
                    .withPropertyValues(
                            "islandride.payment.provider=razorpay",
                            "islandride.payment.live-enabled=true",
                            "islandride.payment.razorpay.key-id=",
                            "islandride.payment.razorpay.key-secret="
                    )
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessage("Payment configuration error: Live Razorpay payments are enabled, but required credentials (key-id, key-secret) are missing.");
                    });
        }
    }
}
