package com.islandride.paymentfinance.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.islandride.administration.service.AuditLogService;
import com.islandride.availability.repository.InventoryHoldRepository;
import com.islandride.booking.domain.Booking;
import com.islandride.booking.domain.BookingStatus;
import com.islandride.booking.domain.PaymentMode;
import com.islandride.booking.repository.BookingRepository;
import com.islandride.booking.repository.OverbookingExceptionRecordRepository;
import com.islandride.booking.repository.SecurityDepositRecordRepository;
import com.islandride.booking.service.BookingLifecycleFoundationService;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.inventory.repository.VehicleUnavailabilityRepository;
import com.islandride.notifications.service.NotificationDispatchService;
import com.islandride.paymentfinance.domain.Payment;
import com.islandride.paymentfinance.domain.PaymentMethod;
import com.islandride.paymentfinance.domain.PaymentStatus;
import com.islandride.paymentfinance.domain.PaymentType;
import com.islandride.paymentfinance.dto.PaymentDtos.GatewayCheckoutOrderResponse;
import com.islandride.paymentfinance.dto.PaymentDtos.MockPaymentSimulateRequest;
import com.islandride.paymentfinance.repository.PaymentAttemptRepository;
import com.islandride.paymentfinance.repository.PaymentRepository;
import com.islandride.paymentfinance.service.PaymentOrchestrationService;
import com.islandride.paymentfinance.service.PaymentStateMachine;
import com.islandride.paymentfinance.service.RefundOrchestrationService;
import com.islandride.pricing.service.BookingPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Phase 7A Step 2 Safety Verification Test:
 * Proves that {@code ISLANDRIDE_PAYMENT_LIVE_ENABLED=false} strictly prevents the Razorpay
 * gateway adapter and orchestration services from dispatching real payment API requests.
 */
@ExtendWith(MockitoExtension.class)
class PaymentLiveModeSafetyVerificationTest {

    private PaymentGatewayProperties properties;
    private ObjectMapper objectMapper;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private AtomicInteger outboundRequestCount;
    private RazorpayGatewayAdapter razorpayAdapter;
    private MockGatewayAdapter mockAdapter;
    private PaymentGatewayRegistry registry;

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentAttemptRepository attemptRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private InventoryHoldRepository holdRepository;
    @Mock
    private VehicleUnavailabilityRepository unavailabilityRepository;
    @Mock
    private SecurityDepositRecordRepository depositRepository;
    @Mock
    private OverbookingExceptionRecordRepository overbookingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentStateMachine paymentStateMachine;
    @Mock
    private BookingLifecycleFoundationService lifecycleService;
    @Mock
    private BookingPolicyService bookingPolicyService;
    @Mock
    private RefundOrchestrationService refundOrchestrationService;
    @Mock
    private NotificationDispatchService notificationDispatchService;
    @Mock
    private AuditLogService auditLogService;

    private PaymentOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        properties = new PaymentGatewayProperties();
        properties.setProvider("razorpay");
        properties.setLiveEnabled(false); // Live payments disabled by default
        properties.getRazorpay().setBaseUrl("https://api.razorpay.com/v1");
        properties.getRazorpay().setKeyId("rzp_test_placeholder_key");
        properties.getRazorpay().setKeySecret("rzp_test_placeholder_secret");
        properties.getRazorpay().setWebhookSecret("rzp_webhook_placeholder_secret");

        objectMapper = new ObjectMapper().findAndRegisterModules();
        outboundRequestCount = new AtomicInteger(0);

        // Interceptor that tracks and fails fast if any actual HTTP request execution is attempted
        ClientHttpRequestInterceptor assertionInterceptor = (request, body, execution) -> {
            outboundRequestCount.incrementAndGet();
            throw new AssertionError(
                    "Violation: Outbound HTTP request was attempted when liveEnabled=false! URI: " + request.getURI()
            );
        };

        restClientBuilder = RestClient.builder().requestInterceptor(assertionInterceptor);
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();

        razorpayAdapter = new RazorpayGatewayAdapter(properties, objectMapper, restClientBuilder);
        mockAdapter = new MockGatewayAdapter(properties);
        registry = new PaymentGatewayRegistry(properties, List.of(razorpayAdapter, mockAdapter));

        orchestrationService = new PaymentOrchestrationService(
                paymentRepository,
                attemptRepository,
                bookingRepository,
                vehicleRepository,
                holdRepository,
                unavailabilityRepository,
                depositRepository,
                overbookingRepository,
                userRepository,
                paymentStateMachine,
                registry,
                properties,
                lifecycleService,
                bookingPolicyService,
                refundOrchestrationService,
                notificationDispatchService,
                auditLogService
        );
    }

    @Nested
    @DisplayName("Scenario 1: Provider is razorpay, live-enabled=false")
    class Scenario1RazorpayLiveDisabled {

        @Test
        @DisplayName("createOrder fails safely with BusinessRuleException and makes zero HTTP requests")
        void createOrder_failsSafelyWithoutHttp() {
            assertThat(razorpayAdapter.isLiveEnabled()).isFalse();

            assertThatThrownBy(() -> razorpayAdapter.createOrder(new CreateGatewayOrderRequest(
                    "rcpt_1001",
                    new BigDecimal("1500.00"),
                    "INR",
                    "idemp_1001",
                    Map.of()
            )))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Live Razorpay gateway operations are disabled. Ensure islandride.payment.live-enabled=true to enable live gateway interactions.");

            // Verify both via interceptor counter and MockRestServiceServer
            assertThat(outboundRequestCount.get()).isEqualTo(0);
            mockServer.verify();
        }

        @Test
        @DisplayName("fetchPaymentStatus fails safely with BusinessRuleException and makes zero HTTP requests")
        void fetchPaymentStatus_failsSafelyWithoutHttp() {
            assertThatThrownBy(() -> razorpayAdapter.fetchPaymentStatus("pay_lookup_1001"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Live Razorpay gateway operations are disabled. Ensure islandride.payment.live-enabled=true to enable live gateway interactions.");

            assertThat(outboundRequestCount.get()).isEqualTo(0);
            mockServer.verify();
        }

        @Test
        @DisplayName("initiateRefund fails safely with BusinessRuleException and makes zero HTTP requests")
        void initiateRefund_failsSafelyWithoutHttp() {
            assertThatThrownBy(() -> razorpayAdapter.initiateRefund(new GatewayRefundRequest(
                    "pay_lookup_1001",
                    new BigDecimal("750.00"),
                    "INR",
                    "User requested cancellation",
                    "idemp_rfnd_1001",
                    Map.of()
            )))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Live Razorpay gateway operations are disabled. Ensure islandride.payment.live-enabled=true to enable live gateway interactions.");

            assertThat(outboundRequestCount.get()).isEqualTo(0);
            mockServer.verify();
        }

        @Test
        @DisplayName("PaymentOrchestrationService.createPaymentCheckoutOrder fails safely without saving records")
        void orchestrationCreateOrder_failsSafelyWithoutPersistingPaymentOrCallingHttp() {
            Booking booking = new Booking();
            booking.setId(42L);
            booking.setPublicId("bkg_safety_001");
            booking.setBookingReference("IR-SAFE-001");
            booking.setStatus(BookingStatus.PENDING_PAYMENT);
            booking.setPaymentDeadlineAt(Instant.now().plus(Duration.ofHours(2)));
            booking.setTotalPayableAmount(new BigDecimal("3000.00"));
            booking.setPickupBalanceDue(new BigDecimal("2250.00"));
            booking.setAdvancePaidAmount(BigDecimal.ZERO);
            booking.setPaymentMode(PaymentMode.PARTIAL_ADVANCE);
            booking.setCurrency("INR");

            when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
            when(paymentRepository.findByBookingIdOrderByCreatedAtDesc(42L)).thenReturn(List.of());

            assertThatThrownBy(() -> orchestrationService.createOrderOnLockedBooking(
                    booking,
                    10L,
                    "idemp_checkout_1",
                    Instant.now()
            ))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Live Razorpay gateway operations are disabled");

            // Zero HTTP calls
            assertThat(outboundRequestCount.get()).isEqualTo(0);
            mockServer.verify();

            // Zero database records created or modified
            verify(paymentRepository, never()).save(any(Payment.class));
            verify(attemptRepository, never()).save(any());
            verify(bookingRepository, never()).save(any(Booking.class));
        }

        @Test
        @DisplayName("reconcileStaleInitiatedPayments skips razorpay payments when liveEnabled=false")
        void reconciliation_skipsRazorpayPaymentsWhenLiveDisabled() {
            Payment stalePayment = new Payment();
            stalePayment.setId(101L);
            stalePayment.setPublicId("pay_stale_001");
            stalePayment.setGatewayProvider("razorpay");
            stalePayment.setGatewayOrderId("order_stale_razorpay");
            stalePayment.setStatus(PaymentStatus.INITIATED);

            when(bookingPolicyService.resolvePaymentReconciliationStaleMinutes()).thenReturn(15);
            when(paymentRepository.findByStatusAndCreatedAtBefore(eq(PaymentStatus.INITIATED), any(Instant.class)))
                    .thenReturn(List.of(stalePayment));

            int count = orchestrationService.reconcileStaleInitiatedPayments();

            assertThat(count).isEqualTo(0);
            // Confirms adapter.fetchPaymentStatus was never invoked
            assertThat(outboundRequestCount.get()).isEqualTo(0);
            mockServer.verify();
        }

        @Test
        @DisplayName("buildMockSimulationPayload rejects requests when provider is razorpay")
        void mockSimulator_rejectsWhenProviderIsRazorpay() {
            MockPaymentSimulateRequest req = new MockPaymentSimulateRequest(
                    "order_razorpay_1",
                    "PAYMENT_CAPTURED",
                    null
            );

            assertThatThrownBy(() -> orchestrationService.buildMockSimulationPayload(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Mock payment simulator is only available when mock gateway mode is active and live mode is off.");

            assertThat(outboundRequestCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("Checkout and webhook signature verification execute offline without network requests")
        void signatureVerification_executesOfflineWithoutNetwork() {
            String orderId = "order_offline_123";
            String paymentId = "pay_offline_456";
            String sig = RazorpaySignatureVerifier.computeHmacHex(
                    orderId + "|" + paymentId,
                    properties.getRazorpay().getKeySecret()
            );

            assertThat(razorpayAdapter.verifyCheckoutSignature(orderId, paymentId, sig)).isTrue();
            assertThat(razorpayAdapter.verifyCheckoutSignature(orderId, paymentId, "invalid_sig")).isFalse();

            String rawPayload = "{\"event\":\"payment.captured\",\"id\":\"evt_offline_789\"}";
            String whSig = RazorpaySignatureVerifier.computeHmacHex(
                    rawPayload,
                    properties.getRazorpay().getWebhookSecret()
            );

            assertThat(razorpayAdapter.verifyWebhookSignature(rawPayload, whSig)).isTrue();
            assertThat(razorpayAdapter.verifyWebhookSignature(rawPayload, "tampered_wh")).isFalse();

            // Strictly zero network requests for cryptographic verification
            assertThat(outboundRequestCount.get()).isEqualTo(0);
            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("Scenario 2: Provider is razorpay, live-enabled=true, valid credentials")
    class Scenario2RazorpayLiveEnabledWithValidCredentials {

        private RestClient.Builder liveClientBuilder;
        private MockRestServiceServer liveMockServer;
        private RazorpayGatewayAdapter liveAdapter;

        @BeforeEach
        void setUpLive() {
            PaymentGatewayProperties liveProps = new PaymentGatewayProperties();
            liveProps.setProvider("razorpay");
            liveProps.setLiveEnabled(true);
            liveProps.getRazorpay().setBaseUrl("https://api.razorpay.com/v1");
            liveProps.getRazorpay().setKeyId("rzp_test_live_key");
            liveProps.getRazorpay().setKeySecret("rzp_test_live_secret");
            liveProps.getRazorpay().setWebhookSecret("rzp_test_live_wh");

            liveClientBuilder = RestClient.builder();
            liveMockServer = MockRestServiceServer.bindTo(liveClientBuilder).build();
            liveAdapter = new RazorpayGatewayAdapter(liveProps, objectMapper, liveClientBuilder);
        }

        @Test
        @DisplayName("createOrder dispatches authenticated POST to /orders against mocked server")
        void createOrder_whenLiveEnabled_submitsAuthenticatedRequest() {
            String mockResponse = """
                    {
                        "id": "order_live_999",
                        "entity": "order",
                        "amount": 100000,
                        "currency": "INR",
                        "receipt": "rcpt_live_1",
                        "status": "created",
                        "created_at": 1700000000
                    }
                    """;

            liveMockServer.expect(requestTo("https://api.razorpay.com/v1/orders"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic cnpwX3Rlc3RfbGl2ZV9rZXk6cnpwX3Rlc3RfbGl2ZV9zZWNyZXQ="))
                    .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(jsonPath("$.amount").value(100000))
                    .andExpect(jsonPath("$.currency").value("INR"))
                    .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

            GatewayOrderResponse response = liveAdapter.createOrder(new CreateGatewayOrderRequest(
                    "rcpt_live_1",
                    new BigDecimal("1000.00"),
                    "INR",
                    "idemp_live_1",
                    Map.of()
            ));

            liveMockServer.verify();
            assertThat(response).isNotNull();
            assertThat(response.gatewayOrderId()).isEqualTo("order_live_999");
            assertThat(response.status()).isEqualTo("created");
        }

        @Test
        @DisplayName("fetchPaymentStatus dispatches GET to /payments/{id} against mocked server")
        void fetchPaymentStatus_whenLiveEnabled_submitsGetRequest() {
            String mockResponse = """
                    {
                        "id": "pay_live_777",
                        "entity": "payment",
                        "amount": 100000,
                        "currency": "INR",
                        "status": "captured",
                        "order_id": "order_live_999",
                        "created_at": 1700000000
                    }
                    """;

            liveMockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_live_777"))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic cnpwX3Rlc3RfbGl2ZV9rZXk6cnpwX3Rlc3RfbGl2ZV9zZWNyZXQ="))
                    .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

            GatewayPaymentStatusResponse response = liveAdapter.fetchPaymentStatus("pay_live_777");

            liveMockServer.verify();
            assertThat(response.gatewayPaymentId()).isEqualTo("pay_live_777");
            assertThat(response.status()).isEqualTo("captured");
        }

        @Test
        @DisplayName("initiateRefund dispatches POST to /payments/{id}/refund against mocked server")
        void initiateRefund_whenLiveEnabled_submitsRefundRequest() {
            String mockResponse = """
                    {
                        "id": "rfnd_live_555",
                        "entity": "refund",
                        "amount": 50000,
                        "currency": "INR",
                        "payment_id": "pay_live_777",
                        "status": "processed",
                        "created_at": 1700000000
                    }
                    """;

            liveMockServer.expect(requestTo("https://api.razorpay.com/v1/payments/pay_live_777/refund"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic cnpwX3Rlc3RfbGl2ZV9rZXk6cnpwX3Rlc3RfbGl2ZV9zZWNyZXQ="))
                    .andExpect(jsonPath("$.amount").value(50000))
                    .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

            GatewayRefundResponse response = liveAdapter.initiateRefund(new GatewayRefundRequest(
                    "pay_live_777",
                    new BigDecimal("500.00"),
                    "INR",
                    "Customer requested refund",
                    "idemp_rfnd_live_1",
                    Map.of()
            ));

            liveMockServer.verify();
            assertThat(response.gatewayRefundId()).isEqualTo("rfnd_live_555");
            assertThat(response.status()).isEqualTo("processed");
        }
    }

    @Nested
    @DisplayName("Scenario 3: Provider is razorpay, live-enabled=true, missing credentials")
    class Scenario3RazorpayLiveEnabledWithMissingCredentials {

        @Test
        @DisplayName("validate() throws IllegalStateException when key-id is missing/blank")
        void validate_failsFastWhenKeyIdMissing() {
            PaymentGatewayProperties props = new PaymentGatewayProperties();
            props.setProvider("razorpay");
            props.setLiveEnabled(true);
            props.getRazorpay().setKeyId("");
            props.getRazorpay().setKeySecret("some_secret");

            assertThatThrownBy(props::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Payment configuration error: Live Razorpay payments are enabled, but required credentials (key-id, key-secret) are missing.")
                    .hasMessageNotContaining("some_secret");
        }

        @Test
        @DisplayName("validate() throws IllegalStateException when key-secret is missing/blank")
        void validate_failsFastWhenKeySecretMissing() {
            PaymentGatewayProperties props = new PaymentGatewayProperties();
            props.setProvider("razorpay");
            props.setLiveEnabled(true);
            props.getRazorpay().setKeyId("some_key_id");
            props.getRazorpay().setKeySecret("   ");

            assertThatThrownBy(props::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Payment configuration error: Live Razorpay payments are enabled, but required credentials (key-id, key-secret) are missing.")
                    .hasMessageNotContaining("some_key_id");
        }

        @Test
        @DisplayName("ApplicationContextRunner aborts startup safely on missing live credentials")
        void contextRunner_failsSafeOnStartup() {
            new ApplicationContextRunner()
                    .withUserConfiguration(PaymentGatewayProperties.class)
                    .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
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

    @Nested
    @DisplayName("Scenario 4: Provider is mock")
    class Scenario4MockProvider {

        @Test
        @DisplayName("Mock adapter creates deterministic order without HTTP")
        void mockAdapter_createsDeterministicOrderWithoutHttp() {
            assertThat(mockAdapter.isLiveEnabled()).isFalse();

            GatewayOrderResponse order1 = mockAdapter.createOrder(new CreateGatewayOrderRequest(
                    "IR-MOCK-1",
                    new BigDecimal("1200.00"),
                    "INR",
                    "idemp_same_seed",
                    Map.of()
            ));

            GatewayOrderResponse order2 = mockAdapter.createOrder(new CreateGatewayOrderRequest(
                    "IR-MOCK-1",
                    new BigDecimal("1200.00"),
                    "INR",
                    "idemp_same_seed",
                    Map.of()
            ));

            assertThat(order1.gatewayOrderId()).startsWith("order_mock_").isEqualTo(order2.gatewayOrderId());
            assertThat(order1.status()).isEqualTo("created");
        }

        @Test
        @DisplayName("Mock adapter initiates refund deterministically without HTTP")
        void mockAdapter_initiatesRefundWithoutHttp() {
            GatewayRefundResponse refund = mockAdapter.initiateRefund(new GatewayRefundRequest(
                    "pay_mock_99",
                    new BigDecimal("600.00"),
                    "INR",
                    "Cancellation",
                    "idemp_rfnd_mock",
                    Map.of()
            ));

            assertThat(refund.gatewayRefundId()).startsWith("rfnd_mock_");
            assertThat(refund.status()).isEqualTo("processed");
        }

        @Test
        @DisplayName("Mock simulator builds payload when provider is mock and liveEnabled=false")
        void mockSimulator_succeedsWhenMockModeActive() {
            PaymentGatewayProperties mockProps = new PaymentGatewayProperties();
            mockProps.setProvider("mock");
            mockProps.setLiveEnabled(false);

            Payment mockPayment = new Payment();
            mockPayment.setGatewayOrderId("order_mock_test_123");
            mockPayment.setAmount(new BigDecimal("1000.00"));
            mockPayment.setCurrency("INR");

            when(paymentRepository.findByGatewayOrderId("order_mock_test_123")).thenReturn(Optional.of(mockPayment));

            PaymentOrchestrationService mockOrchestrationService = new PaymentOrchestrationService(
                    paymentRepository,
                    attemptRepository,
                    bookingRepository,
                    vehicleRepository,
                    holdRepository,
                    unavailabilityRepository,
                    depositRepository,
                    overbookingRepository,
                    userRepository,
                    paymentStateMachine,
                    registry,
                    mockProps,
                    lifecycleService,
                    bookingPolicyService,
                    refundOrchestrationService,
                    notificationDispatchService,
                    auditLogService
            );

            var res = mockOrchestrationService.buildMockSimulationPayload(new MockPaymentSimulateRequest(
                    "order_mock_test_123",
                    "PAYMENT_CAPTURED",
                    null
            ));

            assertThat(res).isNotNull();
            assertThat(res.gatewayOrderId()).isEqualTo("order_mock_test_123");
            assertThat(res.gatewayPaymentId()).startsWith("pay_mock_");
            assertThat(res.checkoutSignature()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Scenario 5: Checkout Order Public Key ID Safety and Leakage Guards")
    class Scenario5CheckoutOrderKeyIdSafetyGuards {

        @Test
        @DisplayName("createOrderOnLockedBooking exposes configured public keyId when provider is razorpay")
        void checkoutOrder_exposesPublicKeyIdWhenRazorpay() {
            Booking booking = new Booking();
            booking.setId(42L);
            booking.setPublicId("bkg_test_pub_42");
            booking.setBookingReference("IR-RZP-42");
            booking.setStatus(BookingStatus.PENDING_PAYMENT);
            booking.setTotalPayableAmount(new BigDecimal("1000.00"));
            booking.setPickupBalanceDue(BigDecimal.ZERO);
            booking.setAdvancePaidAmount(BigDecimal.ZERO);
            booking.setPaymentMode(PaymentMode.FULL);
            booking.setCurrency("INR");
            booking.setPaymentDeadlineAt(Instant.now().plus(Duration.ofMinutes(15)));

            Payment existingPayment = new Payment();
            existingPayment.setId(101L);
            existingPayment.setPublicId("pay_rzp_existing_101");
            existingPayment.setBooking(booking);
            existingPayment.setGatewayProvider("razorpay");
            existingPayment.setGatewayOrderId("order_rzp_mock_123");
            existingPayment.setStatus(PaymentStatus.INITIATED);
            existingPayment.setPaymentMethod(PaymentMethod.GATEWAY);
            existingPayment.setPaymentType(PaymentType.RENTAL_FULL);
            existingPayment.setAmount(new BigDecimal("1000.00"));
            existingPayment.setCurrency("INR");
            existingPayment.setGatewayOrderExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));

            when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
            when(paymentRepository.findByBookingIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(existingPayment));

            GatewayCheckoutOrderResponse response = orchestrationService.createOrderOnLockedBooking(
                    booking,
                    10L,
                    "idemp_key_test_1",
                    Instant.now()
            );

            // 1. Exposes public keyId exactly
            assertThat(response.keyId()).isEqualTo("rzp_test_placeholder_key");
            assertThat(response.gatewayProvider()).isEqualTo("razorpay");
            assertThat(response.gatewayOrderId()).isEqualTo("order_rzp_mock_123");

            // 2. Never exposes private secrets in keyId
            assertThat(response.keyId()).isNotEqualTo("rzp_test_placeholder_secret");
            assertThat(response.keyId()).isNotEqualTo("rzp_webhook_placeholder_secret");

            // 3. String representation does not contain private secrets
            assertThat(response.toString()).doesNotContain("rzp_test_placeholder_secret");
            assertThat(response.toString()).doesNotContain("rzp_webhook_placeholder_secret");
        }

        @Test
        @DisplayName("createOrderOnLockedBooking sets null keyId when provider is mock")
        void checkoutOrder_setsNullKeyIdWhenMock() {
            PaymentGatewayProperties mockProps = new PaymentGatewayProperties();
            mockProps.setProvider("mock");
            mockProps.setLiveEnabled(false);
            mockProps.getRazorpay().setKeyId("rzp_test_hidden_key");
            mockProps.getRazorpay().setKeySecret("rzp_test_hidden_secret");

            PaymentOrchestrationService mockOrchestrationService = new PaymentOrchestrationService(
                    paymentRepository,
                    attemptRepository,
                    bookingRepository,
                    vehicleRepository,
                    holdRepository,
                    unavailabilityRepository,
                    depositRepository,
                    overbookingRepository,
                    userRepository,
                    paymentStateMachine,
                    registry,
                    mockProps,
                    lifecycleService,
                    bookingPolicyService,
                    refundOrchestrationService,
                    notificationDispatchService,
                    auditLogService
            );

            Booking booking = new Booking();
            booking.setId(43L);
            booking.setPublicId("bkg_test_pub_43");
            booking.setBookingReference("IR-MCK-43");
            booking.setStatus(BookingStatus.PENDING_PAYMENT);
            booking.setTotalPayableAmount(new BigDecimal("1000.00"));
            booking.setPickupBalanceDue(BigDecimal.ZERO);
            booking.setAdvancePaidAmount(BigDecimal.ZERO);
            booking.setPaymentMode(PaymentMode.FULL);
            booking.setCurrency("INR");
            booking.setPaymentDeadlineAt(Instant.now().plus(Duration.ofMinutes(15)));

            Payment existingPayment = new Payment();
            existingPayment.setId(102L);
            existingPayment.setPublicId("pay_mock_existing_102");
            existingPayment.setBooking(booking);
            existingPayment.setGatewayProvider("mock");
            existingPayment.setGatewayOrderId("order_mock_456");
            existingPayment.setStatus(PaymentStatus.INITIATED);
            existingPayment.setPaymentMethod(PaymentMethod.GATEWAY);
            existingPayment.setPaymentType(PaymentType.RENTAL_FULL);
            existingPayment.setAmount(new BigDecimal("1000.00"));
            existingPayment.setCurrency("INR");
            existingPayment.setGatewayOrderExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));

            when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
            when(paymentRepository.findByBookingIdOrderByCreatedAtDesc(43L)).thenReturn(List.of(existingPayment));

            GatewayCheckoutOrderResponse response = mockOrchestrationService.createOrderOnLockedBooking(
                    booking,
                    10L,
                    "idemp_key_test_2",
                    Instant.now()
            );

            // Mock mode must not expose any keyId
            assertThat(response.keyId()).isNull();
            assertThat(response.gatewayProvider()).isEqualTo("mock");
        }

        @Test
        @DisplayName("JSON serialization contains keyId without leaking secrets and supports backward compatibility")
        void checkoutOrder_jsonSerializationAndBackwardCompatibility() throws Exception {
            GatewayCheckoutOrderResponse responseWithKey = new GatewayCheckoutOrderResponse(
                    "pay_pub_1",
                    "bkg_pub_1",
                    "IR-REF-1",
                    "razorpay",
                    "order_rzp_999",
                    "rzp_test_public_123",
                    new BigDecimal("1500.00"),
                    "INR",
                    PaymentType.RENTAL_FULL,
                    false,
                    Instant.parse("2026-11-14T12:00:00Z"),
                    Instant.parse("2026-11-14T12:30:00Z")
            );

            String json = objectMapper.writeValueAsString(responseWithKey);

            // Verify public keyId is serialized
            assertThat(json).contains("\"keyId\":\"rzp_test_public_123\"");
            assertThat(json).contains("\"gatewayOrderId\":\"order_rzp_999\"");
            assertThat(json).contains("\"amount\":1500.00");

            // Verify private field names never exist in JSON
            assertThat(json).doesNotContain("keySecret");
            assertThat(json).doesNotContain("webhookSecret");

            // Verify backward-compatible 11-argument constructor
            GatewayCheckoutOrderResponse legacyResponse = new GatewayCheckoutOrderResponse(
                    "pay_pub_2",
                    "bkg_pub_2",
                    "IR-REF-2",
                    "mock",
                    "order_mock_888",
                    new BigDecimal("2000.00"),
                    "INR",
                    PaymentType.RENTAL_FULL,
                    false,
                    Instant.parse("2026-11-14T12:00:00Z"),
                    Instant.parse("2026-11-14T12:30:00Z")
            );

            assertThat(legacyResponse.keyId()).isNull();
            assertThat(legacyResponse.gatewayOrderId()).isEqualTo("order_mock_888");
        }
    }
}
