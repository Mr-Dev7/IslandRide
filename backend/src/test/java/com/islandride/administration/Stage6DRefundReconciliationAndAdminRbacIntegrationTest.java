package com.islandride.administration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.islandride.administration.api.AdminOperationsAndFinanceController;
import com.islandride.administration.service.AdminFinanceOverviewService;
import com.islandride.booking.domain.*;
import com.islandride.booking.dto.BookingDtos;
import com.islandride.booking.repository.BookingRepository;
import com.islandride.booking.repository.SecurityDepositRecordRepository;
import com.islandride.booking.service.CancellationPolicySnapshot;
import com.islandride.booking.service.CancellationSettlementBreakdown;
import com.islandride.booking.service.CancellationSnapshotService;
import com.islandride.core.security.JwtTokenProvider;
import com.islandride.core.security.UserPrincipal;
import com.islandride.geography.domain.Island;
import com.islandride.geography.domain.Location;
import com.islandride.geography.repository.IslandRepository;
import com.islandride.geography.repository.LocationRepository;
import com.islandride.identityaccess.domain.Role;
import com.islandride.identityaccess.domain.RoleType;
import com.islandride.identityaccess.domain.User;
import com.islandride.identityaccess.domain.UserStatus;
import com.islandride.identityaccess.repository.RoleRepository;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.inventory.domain.BookingMode;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.domain.VehicleCategory;
import com.islandride.inventory.domain.VehicleStatus;
import com.islandride.inventory.repository.VehicleCategoryRepository;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.PartnerStaff;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.partner.repository.BusinessRepository;
import com.islandride.partner.repository.PartnerStaffRepository;
import com.islandride.paymentfinance.domain.*;
import com.islandride.paymentfinance.dto.PaymentDtos;
import com.islandride.paymentfinance.repository.PaymentRepository;
import com.islandride.paymentfinance.repository.RefundRepository;
import com.islandride.paymentfinance.service.PaymentOrchestrationService;
import com.islandride.paymentfinance.service.PaymentReconciliationScheduler;
import com.islandride.paymentfinance.service.RefundOrchestrationService;
import com.islandride.paymentfinance.service.RefundRetryScheduler;
import com.islandride.pricing.domain.PartnerPolicy;
import com.islandride.pricing.repository.PartnerPolicyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class Stage6DRefundReconciliationAndAdminRbacIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private PartnerStaffRepository partnerStaffRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleCategoryRepository categoryRepository;

    @Autowired
    private IslandRepository islandRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private SecurityDepositRecordRepository depositRepository;

    @Autowired
    private PartnerPolicyRepository partnerPolicyRepository;

    @Autowired
    private CancellationSnapshotService cancellationSnapshotService;

    @Autowired
    private RefundOrchestrationService refundOrchestrationService;

    @Autowired
    private PaymentOrchestrationService paymentOrchestrationService;

    @Autowired
    private AdminFinanceOverviewService adminFinanceOverviewService;

    @Autowired(required = false)
    private RefundRetryScheduler refundRetryScheduler;

    @Autowired(required = false)
    private PaymentReconciliationScheduler paymentReconciliationScheduler;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final List<Long> businessesToClean = new CopyOnWriteArrayList<>();
    private final List<Long> usersToClean = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            for (Long bizId : businessesToClean) {
                entityManager.createQuery("DELETE FROM SecurityDepositRecord sdr WHERE sdr.booking.business.id = :bid")
                        .setParameter("bid", bizId).executeUpdate();
                entityManager.createQuery("DELETE FROM Refund r WHERE r.booking.business.id = :bid")
                        .setParameter("bid", bizId).executeUpdate();
                entityManager.createQuery("DELETE FROM Payment p WHERE p.booking.business.id = :bid")
                        .setParameter("bid", bizId).executeUpdate();
                entityManager.createQuery("DELETE FROM Booking b WHERE b.business.id = :bid")
                        .setParameter("bid", bizId).executeUpdate();
                entityManager.createQuery("DELETE FROM Vehicle v WHERE v.business.id = :bid")
                        .setParameter("bid", bizId).executeUpdate();
                entityManager.createQuery("DELETE FROM PartnerStaff ps WHERE ps.business.id = :bid")
                        .setParameter("bid", bizId).executeUpdate();
                entityManager.createQuery("DELETE FROM PartnerPolicy pp WHERE pp.business.id = :bid")
                        .setParameter("bid", bizId).executeUpdate();
                entityManager.createQuery("DELETE FROM Business b WHERE b.id = :bid")
                        .setParameter("bid", bizId).executeUpdate();
            }
            for (Long uid : usersToClean) {
                entityManager.createQuery("DELETE FROM User u WHERE u.id = :uid")
                        .setParameter("uid", uid).executeUpdate();
            }
        });
        businessesToClean.clear();
        usersToClean.clear();
    }

    // -------------------------------------------------------------------------
    // 1. RBAC: OPERATIONS_ADMIN Permissions & Lockdowns
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Admin RBAC: OPERATIONS_ADMIN can govern bookings and disruptions, but is strictly blocked from finance endpoints (403)")
    void testAdminRbac_OperationsAdminPermissions() throws Exception {
        User opsAdmin = createTestUser("ops_rbac_" + randomSuffix() + "@islandride.test", RoleType.ROLE_OPERATIONS_ADMIN);
        String opsToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(opsAdmin, null));

        Booking booking = createSampleBooking(BookingStatus.CONFIRMED);

        // 1. Can view bookings list (200 OK)
        mockMvc.perform(get("/api/v1/admin/bookings")
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isOk());

        // 2. Can apply disruption override (200 OK)
        BookingDtos.DisruptionOverrideRequest disruptionReq = new BookingDtos.DisruptionOverrideRequest(
                DisruptionExceptionType.WEATHER_DISRUPTION,
                "Cyclonic weather warning over South Andaman",
                false
        );
        mockMvc.perform(post("/api/v1/admin/bookings/" + booking.getPublicId() + "/disruption-override")
                        .with(csrf())
                        .header("Authorization", "Bearer " + opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disruptionReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("CANCELLED"));

        // 3. Can trigger booking expiration sweep (200 OK)
        mockMvc.perform(post("/api/v1/admin/bookings/expire-sweep")
                        .with(csrf())
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // 4. FORBIDDEN from finance overview (403)
        mockMvc.perform(get("/api/v1/admin/finance/overview")
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isForbidden());

        // 5. FORBIDDEN from issuing manual refunds (403)
        PaymentDtos.ManualRefundRequest refundReq = new PaymentDtos.ManualRefundRequest(
                new BigDecimal("500.00"),
                "Unauthorized ops refund attempt",
                RefundType.PARTIAL_ADJUSTMENT
        );
        mockMvc.perform(post("/api/v1/admin/finance/refunds/manual/" + booking.getPublicId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundReq)))
                .andExpect(status().isForbidden());

        // 6. FORBIDDEN from triggering payment reconciliation sweep (403)
        mockMvc.perform(post("/api/v1/admin/finance/reconcile-payments")
                        .with(csrf())
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // 2. RBAC: FINANCE_ADMIN Permissions & Lockdowns
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Admin RBAC: FINANCE_ADMIN can govern ledgers, manual refunds and reconciliations, but is blocked from operations disruptions and moderation (403)")
    void testAdminRbac_FinanceAdminPermissions() throws Exception {
        User finAdmin = createTestUser("fin_rbac_" + randomSuffix() + "@islandride.test", RoleType.ROLE_FINANCE_ADMIN);
        String finToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(finAdmin, null));

        Booking booking = createSampleBooking(BookingStatus.CONFIRMED);
        Payment payment = createSamplePayment(booking, new BigDecimal("2000.00"), PaymentStatus.CAPTURED);

        // 1. Can view finance overview (200 OK)
        mockMvc.perform(get("/api/v1/admin/finance/overview")
                        .header("Authorization", "Bearer " + finToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositTrustSummary").exists());

        // 2. Can trigger payment reconciliation sweep (200 OK)
        mockMvc.perform(post("/api/v1/admin/finance/reconcile-payments")
                        .with(csrf())
                        .header("Authorization", "Bearer " + finToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // 3. Can issue manual refund on captured payment (200 OK)
        PaymentDtos.ManualRefundRequest refundReq = new PaymentDtos.ManualRefundRequest(
                new BigDecimal("500.00"),
                "Authorized finance refund",
                RefundType.PARTIAL_ADJUSTMENT
        );
        mockMvc.perform(post("/api/v1/admin/finance/refunds/manual/" + booking.getPublicId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + finToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(500.00));

        // 4. FORBIDDEN from operations disruption overrides (403)
        BookingDtos.DisruptionOverrideRequest disruptionReq = new BookingDtos.DisruptionOverrideRequest(
                DisruptionExceptionType.FERRY_CANCELLATION,
                "Unauthorized finance disruption override attempt",
                false
        );
        mockMvc.perform(post("/api/v1/admin/bookings/" + booking.getPublicId() + "/disruption-override")
                        .with(csrf())
                        .header("Authorization", "Bearer " + finToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disruptionReq)))
                .andExpect(status().isForbidden());

        // 5. FORBIDDEN from vehicle catalog moderation (403)
        mockMvc.perform(get("/api/v1/admin/vehicles")
                        .header("Authorization", "Bearer " + finToken))
                .andExpect(status().isForbidden());

        // 6. FORBIDDEN from booking expiration sweep (403)
        mockMvc.perform(post("/api/v1/admin/bookings/expire-sweep")
                        .with(csrf())
                        .header("Authorization", "Bearer " + finToken))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // 3. RBAC: SUPPORT_AGENT Read-Only Permissions & Mutation Lockdowns
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Admin RBAC: SUPPORT_AGENT can view bookings and users, but is strictly blocked from all mutations and finance (403)")
    void testAdminRbac_SupportAgentPermissions() throws Exception {
        User supportAgent = createTestUser("sup_rbac_" + randomSuffix() + "@islandride.test", RoleType.ROLE_SUPPORT_AGENT);
        String supportToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(supportAgent, null));

        Booking booking = createSampleBooking(BookingStatus.CONFIRMED);

        // 1. Can view admin bookings list (200 OK)
        mockMvc.perform(get("/api/v1/admin/bookings")
                        .header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isOk());

        // 2. Can view single booking detail (200 OK)
        mockMvc.perform(get("/api/v1/admin/bookings/" + booking.getPublicId())
                        .header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.publicId").value(booking.getPublicId()));

        // 3. Can view platform users list (200 OK)
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isOk());

        // 4. FORBIDDEN from finance overview (403)
        mockMvc.perform(get("/api/v1/admin/finance/overview")
                        .header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isForbidden());

        // 5. FORBIDDEN from disruption overrides (403)
        BookingDtos.DisruptionOverrideRequest disruptionReq = new BookingDtos.DisruptionOverrideRequest(
                DisruptionExceptionType.WEATHER_DISRUPTION,
                "Unauthorized support disruption attempt",
                false
        );
        mockMvc.perform(post("/api/v1/admin/bookings/" + booking.getPublicId() + "/disruption-override")
                        .with(csrf())
                        .header("Authorization", "Bearer " + supportToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disruptionReq)))
                .andExpect(status().isForbidden());

        // 6. FORBIDDEN from manual refunds (403)
        PaymentDtos.ManualRefundRequest refundReq = new PaymentDtos.ManualRefundRequest(
                new BigDecimal("200.00"),
                "Unauthorized support refund attempt",
                RefundType.PARTIAL_ADJUSTMENT
        );
        mockMvc.perform(post("/api/v1/admin/finance/refunds/manual/" + booking.getPublicId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + supportToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundReq)))
                .andExpect(status().isForbidden());

        // 7. FORBIDDEN from vehicle catalog moderation (403)
        mockMvc.perform(get("/api/v1/admin/vehicles")
                        .header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // 4. RBAC: Non-Admin Personas Blocked Completely
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Admin RBAC: TOURIST and PARTNER roles are strictly forbidden from all admin endpoints (403)")
    void testAdminRbac_TouristAndPartnerBlocked() throws Exception {
        User tourist = createTestUser("tourist_adm_" + randomSuffix() + "@islandride.test", RoleType.ROLE_TOURIST);
        String touristToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(tourist, null));

        User partner = createTestUser("partner_adm_" + randomSuffix() + "@islandride.test", RoleType.ROLE_PARTNER_OWNER);
        String partnerToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(partner, 1L));

        // Tourist blocked
        mockMvc.perform(get("/api/v1/admin/bookings")
                        .header("Authorization", "Bearer " + touristToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/finance/overview")
                        .header("Authorization", "Bearer " + touristToken))
                .andExpect(status().isForbidden());

        // Partner blocked
        mockMvc.perform(get("/api/v1/admin/bookings")
                        .header("Authorization", "Bearer " + partnerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/finance/overview")
                        .header("Authorization", "Bearer " + partnerToken))
                .andExpect(status().isForbidden());

        // Unauthenticated request returns 401
        mockMvc.perform(get("/api/v1/admin/bookings"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // 5. Cancellation Snapshot Immutability
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Cancellation Engine: Modifying live PartnerPolicy in DB does NOT alter refund calculation for pre-existing booking snapshot")
    void testCancellationSnapshot_ImmutabilityAgainstLivePolicyMutation() {
        User owner = createTestUser("owner_snap_" + randomSuffix() + "@islandride.test", RoleType.ROLE_PARTNER_OWNER);
        Business biz = createTestBusiness(owner, "Snap Fleet " + randomSuffix());

        // Initial policy: 10% cancellation fee, 24h deadline, deposit 1500.00
        PartnerPolicy policy = new PartnerPolicy();
        policy.setBusiness(biz);
        policy.setCancellationDeadlineHours(24);
        policy.setFreeCancellationAllowed(true);
        policy.setCancellationFeePercentage(new BigDecimal("10.00"));
        policy.setNoShowGraceMinutes(60);
        policy.setNoShowPenaltyPercentage(new BigDecimal("100.00"));
        partnerPolicyRepository.save(policy);

        // 1. Capture snapshot at booking creation time
        CancellationPolicySnapshot snapshot = cancellationSnapshotService.captureSnapshot(
                biz.getId(),
                new BigDecimal("1500.00"),
                Instant.now()
        );
        assertThat(snapshot.defaultFeePercentage()).isEqualByComparingTo("10.00");
        assertThat(snapshot.securityDepositAmount()).isEqualByComparingTo("1500.00");

        // 2. Mutate live partner policy in database (e.g. partner raises cancellation fee to 50%)
        policy.setCancellationFeePercentage(new BigDecimal("50.00"));
        partnerPolicyRepository.saveAndFlush(policy);

        // 3. Evaluate cancellation using the frozen snapshot: rental 2000.00, within 12h (past free deadline)
        Instant pickup = Instant.now().plus(Duration.ofHours(12));
        CancellationSettlementBreakdown breakdown = cancellationSnapshotService.evaluateCancellation(
                snapshot,
                pickup,
                Instant.now(),
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("1500.00"),
                BigDecimal.ZERO
        );

        // Assert: penalty is calculated at snapshot's 10% (₹200.00), NOT the updated live 50% (₹1000.00)
        assertThat(breakdown.rentalPenaltyAmount()).isEqualByComparingTo("200.00");
        assertThat(breakdown.netRentalRefundAmount()).isEqualByComparingTo("1800.00");
        // Assert: security deposit is 100% refunded (₹1500.00), totally isolated from penalty
        assertThat(breakdown.securityDepositRefundAmount()).isEqualByComparingTo("1500.00");
        assertThat(breakdown.totalRefundAmount()).isEqualByComparingTo("3300.00");
    }

    // -------------------------------------------------------------------------
    // 6. Asynchronous Refund Retry Scheduler
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Schedulers: RefundRetryScheduler sweeps due FAILED refunds and advances status using mock gateway")
    void testRefundRetryScheduler_RetriesFailedRefund_WithBackoff() {
        Booking booking = createSampleBooking(BookingStatus.CONFIRMED);
        Payment payment = createSamplePayment(booking, new BigDecimal("1000.00"), PaymentStatus.CAPTURED);
        payment.setGatewayPaymentId("pay_mock_test_retry");
        paymentRepository.save(payment);

        // Seed a FAILED refund with nextRetryAt in the past
        Refund refund = new Refund();
        refund.setBooking(booking);
        refund.setPayment(payment);
        refund.setAmount(new BigDecimal("500.00"));
        refund.setGatewayFeeRetained(BigDecimal.ZERO);
        refund.setCurrency("INR");
        refund.setRefundType(RefundType.RENTAL);
        refund.setReason("Test failure sweep");
        refund.setStatus(RefundStatus.FAILED);
        refund.setRetryCount(1);
        refund.setNextRetryAt(Instant.now().minus(Duration.ofMinutes(5)));
        refund.setIdempotencyKey("idemp_retry_" + randomSuffix());
        refund = refundRepository.save(refund);

        // Execute retry scheduler sweep directly via RefundRetryScheduler
        RefundRetryScheduler scheduler = refundRetryScheduler != null
                ? refundRetryScheduler
                : new RefundRetryScheduler(refundOrchestrationService);
        scheduler.runRefundRetrySweep();

        // Assert: In mock mode, the retry succeeded and status advanced to RECONCILED (or SUCCESS)
        Refund updated = refundRepository.findById(refund.getId()).orElseThrow();
        assertThat(updated.getStatus()).isIn(RefundStatus.SUCCESS, RefundStatus.RECONCILED);
        assertThat(updated.getGatewayRefundId()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // 7. Payment Reconciliation Scheduler
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Schedulers: PaymentReconciliationScheduler recovers stale INITIATED payments against mock gateway")
    void testPaymentReconciliationScheduler_RecoversStaleInitiatedPayment() {
        Booking booking = createSampleBooking(BookingStatus.PENDING_PAYMENT);

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(new BigDecimal("1200.00"));
        payment.setCurrency("INR");
        payment.setPaymentMethod(PaymentMethod.GATEWAY);
        payment.setGatewayProvider(com.islandride.paymentfinance.gateway.MockGatewayAdapter.PROVIDER_CODE);
        payment.setGatewayOrderId("order_mock_stale_" + randomSuffix());
        payment.setGatewayPaymentId("pay_mock_stale_" + randomSuffix());
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setIdempotencyKey("pay_idemp_stale_" + randomSuffix());
        payment.setDeposit(false);
        // Backdate createdAt to 120 minutes ago (well past the 60m reconciliation threshold)
        payment.setCreatedAt(Instant.now().minus(Duration.ofMinutes(120)));
        payment = paymentRepository.save(payment);

        final Long targetPaymentId = payment.getId();

        // Update database row directly to preserve past createdAt timestamp
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createQuery("UPDATE Payment p SET p.createdAt = :past WHERE p.id = :id")
                    .setParameter("past", Instant.now().minus(Duration.ofMinutes(120)))
                    .setParameter("id", targetPaymentId)
                    .executeUpdate();
        });

        // Run reconciliation scheduler directly via PaymentReconciliationScheduler
        PaymentReconciliationScheduler scheduler = paymentReconciliationScheduler != null
                ? paymentReconciliationScheduler
                : new PaymentReconciliationScheduler(paymentOrchestrationService);
        scheduler.runPaymentReconciliationSweep();

        // Assert: Payment status is no longer INITIATED
        Payment reconciledPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(reconciledPayment.getStatus()).isIn(PaymentStatus.CAPTURED, PaymentStatus.FAILED);
    }

    // -------------------------------------------------------------------------
    // 8. Deposit Trust Ledger: Technical Accounting Invariants
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Deposit Ledger: computeDepositTrustSummary mathematically balances expected, held, released and forfeited amounts")
    void testDepositTrustLedger_AccuratelyAggregatesBalances() {
        // Seed 4 deposit records representing each state, each attached to its own unique booking
        createDepositRecord(createSampleBooking(BookingStatus.CONFIRMED), new BigDecimal("2000.00"), new BigDecimal("2000.00"), BigDecimal.ZERO, BigDecimal.ZERO, SecurityDepositStatus.COLLECTED);
        createDepositRecord(createSampleBooking(BookingStatus.CONFIRMED), new BigDecimal("1500.00"), new BigDecimal("1500.00"), BigDecimal.ZERO, BigDecimal.ZERO, SecurityDepositStatus.INSPECTION_HOLD);
        createDepositRecord(createSampleBooking(BookingStatus.CONFIRMED), new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("1000.00"), BigDecimal.ZERO, SecurityDepositStatus.RELEASED);
        createDepositRecord(createSampleBooking(BookingStatus.CONFIRMED), new BigDecimal("500.00"), new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00"), SecurityDepositStatus.FORFEITED);

        PaymentDtos.SecurityDepositTrustSummaryDto summary = adminFinanceOverviewService.computeDepositTrustSummary();

        // Assert: Total expected includes all 4 deposits (at least ₹5000.00)
        assertThat(summary.totalExpectedDeposits()).isGreaterThanOrEqualTo(new BigDecimal("5000.00"));
        assertThat(summary.totalCollectedHeldLiability()).isGreaterThanOrEqualTo(new BigDecimal("2000.00"));
        assertThat(summary.totalInspectionHoldAmount()).isGreaterThanOrEqualTo(new BigDecimal("1500.00"));
        assertThat(summary.totalReleasedDeposits()).isGreaterThanOrEqualTo(new BigDecimal("1000.00"));
        assertThat(summary.totalForfeitedDeposits()).isGreaterThanOrEqualTo(new BigDecimal("500.00"));
        assertThat(summary.activeHeldCount()).isGreaterThanOrEqualTo(1);
        assertThat(summary.inspectionHoldCount()).isGreaterThanOrEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // 9. Manual Refund: Boundary Validation & Idempotency
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Manual Refunds: Cannot exceed remaining refundable balance and prevents duplicate executions via idempotency")
    void testManualRefund_CannotExceedCapturedAmount_AndIsIdempotent() {
        User finAdmin = createTestUser("fin_man_" + randomSuffix() + "@islandride.test", RoleType.ROLE_FINANCE_ADMIN);
        Booking booking = createSampleBooking(BookingStatus.CONFIRMED);
        Payment payment = createSamplePayment(booking, new BigDecimal("2000.00"), PaymentStatus.CAPTURED);
        payment.setGatewayPaymentId("pay_mock_man_" + randomSuffix());
        paymentRepository.save(payment);

        // Authenticate as Finance Admin
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        UserPrincipal.create(finAdmin, null), null, UserPrincipal.create(finAdmin, null).getAuthorities()
                )
        );

        String idempKey = "ref_idemp_key_" + randomSuffix();

        // 1. Issue partial refund for 1200.00
        PaymentDtos.RefundSummaryDto ref1 = refundOrchestrationService.createManualRefund(
                booking.getPublicId(),
                new BigDecimal("1200.00"),
                "First partial refund",
                RefundType.PARTIAL_ADJUSTMENT,
                finAdmin.getId(),
                idempKey
        );
        assertThat(ref1.amount()).isEqualByComparingTo("1200.00");

        // 2. Replay with identical idempotency key -> Returns original refund idempotently without duplicate deduction
        PaymentDtos.RefundSummaryDto refReplay = refundOrchestrationService.createManualRefund(
                booking.getPublicId(),
                new BigDecimal("1200.00"),
                "Replayed request",
                RefundType.PARTIAL_ADJUSTMENT,
                finAdmin.getId(),
                idempKey
        );
        assertThat(refReplay.publicId()).isEqualTo(ref1.publicId());

        // 3. Attempting to refund 1000.00 when only 800.00 remains -> Rejected with BusinessRuleException
        assertThatThrownBy(() -> refundOrchestrationService.createManualRefund(
                booking.getPublicId(),
                new BigDecimal("1000.00"),
                "Over-refund attempt",
                RefundType.PARTIAL_ADJUSTMENT,
                finAdmin.getId(),
                "ref_over_" + randomSuffix()
        )).isInstanceOf(com.islandride.core.exception.BusinessRuleException.class)
                .hasMessageContaining("exceeds remaining refundable balance");
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------
    private Booking createSampleBooking(BookingStatus initialStatus) {
        String token = randomSuffix();
        User owner = createTestUser("bkg_owner_" + token + "@islandride.test", RoleType.ROLE_PARTNER_OWNER);
        Business biz = createTestBusiness(owner, "Sample Fleet " + token);
        User tourist = createTestUser("bkg_tourist_" + token + "@islandride.test", RoleType.ROLE_TOURIST);
        Vehicle vehicle = createTestVehicle(biz, "Scooter " + token);

        Booking booking = new Booking();
        booking.setUser(tourist);
        booking.setVehicle(vehicle);
        booking.setBusiness(biz);
        booking.setPickupLocation(vehicle.getPickupLocation());
        booking.setReturnLocation(vehicle.getReturnLocation());
        booking.setBookingReference("BK-6D-" + token);
        booking.setStartTime(Instant.now().plus(Duration.ofDays(1)));
        booking.setEndTime(Instant.now().plus(Duration.ofDays(3)));
        booking.setTotalDays(2);
        booking.setDailyRate(new BigDecimal("1000.00"));
        booking.setBaseRentalAmount(new BigDecimal("2000.00"));
        booking.setTotalPayableAmount(new BigDecimal("2000.00"));
        booking.setAdvancePaidAmount(new BigDecimal("2000.00"));
        booking.setStatus(initialStatus);
        booking.setBookingMode(BookingMode.INSTANT);
        booking.setPaymentMode(PaymentMode.FULL);
        CancellationPolicySnapshot snapshot = cancellationSnapshotService.captureSnapshot(biz.getId(), new BigDecimal("1500.00"), Instant.now());
        booking.setCancellationPolicySnapshot(cancellationSnapshotService.serialize(snapshot));
        return bookingRepository.save(booking);
    }

    private Payment createSamplePayment(Booking booking, BigDecimal amount, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(amount);
        payment.setCurrency("INR");
        payment.setPaymentMethod(PaymentMethod.GATEWAY);
        payment.setPaymentType(PaymentType.RENTAL_FULL);
        payment.setStatus(status);
        payment.setGatewayProvider(com.islandride.paymentfinance.gateway.MockGatewayAdapter.PROVIDER_CODE);
        payment.setGatewayOrderId("order_mock_" + randomSuffix());
        payment.setGatewayPaymentId("pay_mock_" + randomSuffix());
        payment.setIdempotencyKey("pay_idemp_" + randomSuffix());
        payment.setDeposit(false);
        return paymentRepository.save(payment);
    }

    private SecurityDepositRecord createDepositRecord(Booking booking,
                                                      BigDecimal expected,
                                                      BigDecimal collected,
                                                      BigDecimal refunded,
                                                      BigDecimal forfeited,
                                                      SecurityDepositStatus status) {
        SecurityDepositRecord record = new SecurityDepositRecord();
        record.setBooking(booking);
        record.setExpectedAmount(expected);
        record.setCollectedAmount(collected);
        record.setRefundedAmount(refunded);
        record.setForfeitedAmount(forfeited);
        record.setCurrency("INR");
        record.setStatus(status);
        record.setCollectionMethod(SecurityDepositCollectionMethod.AT_PICKUP);
        return depositRepository.save(record);
    }

    private User createTestUser(String email, RoleType roleType) {
        Role role = roleRepository.findByName(roleType.name()).orElseThrow();
        User user = new User();
        user.setEmail(email.toLowerCase(Locale.ROOT));
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPhone("+9197" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100000000));
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(role);
        user = userRepository.save(user);
        usersToClean.add(user.getId());
        return user;
    }

    private Business createTestBusiness(User owner, String tradeName) {
        Business biz = new Business();
        biz.setOwnerUser(owner);
        biz.setTradeName(tradeName);
        biz.setLegalName(tradeName + " Legal Entity");
        biz.setGstin("35AABCA1234F1Z0");
        biz.setPanNumber("AABCA1234F");
        biz.setVerificationStatus(VerificationStatus.APPROVED);
        biz = businessRepository.save(biz);
        businessesToClean.add(biz.getId());

        PartnerStaff ownerStaff = new PartnerStaff();
        ownerStaff.setBusiness(biz);
        ownerStaff.setUser(owner);
        ownerStaff.setRoleInBusiness("OWNER");
        ownerStaff.setActive(true);
        ownerStaff.setCanRecordHandover(true);
        ownerStaff.setCanCollectOfflinePayments(true);
        ownerStaff.setCanMarkNoShow(true);
        partnerStaffRepository.save(ownerStaff);
        return biz;
    }

    private Vehicle createTestVehicle(Business biz, String title) {
        Island island = islandRepository.findByCode("PORT_BLAIR").orElseGet(() -> islandRepository.findAll().getFirst());
        Location loc = locationRepository.findByIslandIdAndActiveTrue(island.getId()).getFirst();
        VehicleCategory cat = categoryRepository.findAll().getFirst();

        Vehicle v = new Vehicle();
        v.setBusiness(biz);
        v.setIsland(island);
        v.setCategory(cat);
        v.setPickupLocation(loc);
        v.setReturnLocation(loc);
        v.setTitle(title);
        v.setMake("Honda");
        v.setModel("Activa 6G");
        v.setModelYear(2025);
        v.setRegistrationNumber("AN01" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT));
        v.setDailyRate(new BigDecimal("800.00"));
        v.setSecurityDeposit(new BigDecimal("1500.00"));
        v.setBookingMode(BookingMode.INSTANT);
        v.setStatus(VehicleStatus.ACTIVE);
        return vehicleRepository.save(v);
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
