package com.islandride.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.islandride.administration.repository.AuditLogRepository;
import com.islandride.administration.service.AdminFinanceOverviewService;
import com.islandride.availability.domain.HoldPurpose;
import com.islandride.availability.domain.HoldStatus;
import com.islandride.availability.repository.InventoryHoldRepository;
import com.islandride.availability.service.InventoryHoldService;
import com.islandride.booking.domain.*;
import com.islandride.booking.dto.BookingDtos;
import com.islandride.booking.repository.*;
import com.islandride.booking.service.BookingExpirationScheduler;
import com.islandride.booking.service.BookingLifecycleFoundationService;
import com.islandride.booking.service.BookingOrchestrationService;
import com.islandride.booking.service.PublicBookingQuoteService;
import com.islandride.commission.domain.Commission;
import com.islandride.commission.domain.CommissionStatus;
import com.islandride.commission.repository.CommissionRepository;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.ConflictException;
import com.islandride.core.exception.IdempotencyConflictException;
import com.islandride.core.security.TenantSecurityService;
import com.islandride.core.security.UserPrincipal;
import com.islandride.geography.domain.Island;
import com.islandride.geography.domain.Location;
import com.islandride.geography.repository.IslandRepository;
import com.islandride.geography.repository.LocationRepository;
import com.islandride.identityaccess.domain.Role;
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
import com.islandride.inventory.repository.VehicleUnavailabilityRepository;
import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.BusinessLocation;
import com.islandride.partner.domain.BusinessLocationId;
import com.islandride.partner.domain.PartnerStaff;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.partner.repository.BusinessLocationRepository;
import com.islandride.partner.repository.BusinessRepository;
import com.islandride.partner.repository.PartnerStaffRepository;
import com.islandride.paymentfinance.domain.*;
import com.islandride.paymentfinance.dto.PaymentDtos;
import com.islandride.paymentfinance.gateway.PaymentGatewayProperties;
import com.islandride.paymentfinance.repository.*;
import com.islandride.paymentfinance.service.PaymentOrchestrationService;
import com.islandride.paymentfinance.service.PaymentReconciliationScheduler;
import com.islandride.paymentfinance.service.RefundOrchestrationService;
import com.islandride.paymentfinance.service.RefundRetryScheduler;
import com.islandride.paymentfinance.service.WebhookProcessingService;
import com.islandride.pricing.domain.PartnerPolicy;
import com.islandride.pricing.repository.PartnerOperatingHoursRepository;
import com.islandride.pricing.repository.PartnerPolicyRepository;
import com.islandride.pricing.service.BookingPolicyService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
class Stage6BBookingAndPaymentOrchestrationIntegrationTest {

    @Autowired
    private PublicBookingQuoteService publicBookingQuoteService;
    @Autowired
    private InventoryHoldService holdService;
    @Autowired
    private BookingOrchestrationService bookingOrchestrationService;
    @Autowired
    private PaymentOrchestrationService paymentOrchestrationService;
    @Autowired
    private RefundOrchestrationService refundOrchestrationService;
    @Autowired
    private WebhookProcessingService webhookProcessingService;
    @Autowired
    private AdminFinanceOverviewService adminFinanceOverviewService;
    @Autowired
    private BookingPolicyService bookingPolicyService;
    @Autowired
    private TenantSecurityService tenantSecurityService;
    @Autowired
    private PaymentGatewayProperties paymentGatewayProperties;

    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private BookingStatusHistoryRepository statusHistoryRepository;
    @Autowired
    private BookingModificationRepository modificationRepository;
    @Autowired
    private HandoverInspectionRepository handoverRepository;
    @Autowired
    private SecurityDepositRecordRepository depositRepository;
    @Autowired
    private OverbookingExceptionRecordRepository overbookingRepository;
    @Autowired
    private InventoryHoldRepository holdRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private OfflineCollectionRepository offlineCollectionRepository;
    @Autowired
    private PaymentWebhookEventRepository webhookRepository;
    @Autowired
    private CommissionRepository commissionRepository;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private VehicleCategoryRepository categoryRepository;
    @Autowired
    private VehicleUnavailabilityRepository unavailabilityRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private BusinessLocationRepository businessLocationRepository;
    @Autowired
    private PartnerStaffRepository staffRepository;
    @Autowired
    private PartnerPolicyRepository partnerPolicyRepository;
    @Autowired
    private PartnerOperatingHoursRepository operatingHoursRepository;
    @Autowired
    private IslandRepository islandRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Fixture> fixturesToClean = new CopyOnWriteArrayList<>();

    private record Fixture(
            Long ownerUserId,
            Long managerUserId,
            Long staffUserId,
            Long touristUserId,
            Long adminUserId,
            Long bizId,
            Long vehicleId,
            String vehiclePublicId,
            Long islandId,
            Long pickupLocationId,
            Long returnLocationId,
            String token,
            List<Long> extraUserIds
    ) {}

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        for (Fixture fx : fixturesToClean) {
            cleanFixture(fx);
        }
        fixturesToClean.clear();
    }

    private void cleanFixture(Fixture fx) {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            List<Long> allUserIds = new ArrayList<>(List.of(
                    fx.ownerUserId(), fx.managerUserId(), fx.staffUserId(), fx.touristUserId(), fx.adminUserId()
            ));
            allUserIds.addAll(fx.extraUserIds());

            List<Booking> bookings = bookingRepository.findByBusinessIdOrderByCreatedAtDesc(fx.bizId());
            for (Booking b : bookings) {
                entityManager.createQuery("DELETE FROM AuditLog a WHERE a.entityName = 'Booking' AND a.entityId = :pid")
                        .setParameter("pid", b.getPublicId()).executeUpdate();
                entityManager.createQuery("DELETE FROM OverbookingExceptionRecord o WHERE o.booking.id = :bid OR o.conflictingBooking.id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
                entityManager.createQuery("DELETE FROM PaymentWebhookEvent w WHERE w.booking.id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
                entityManager.createQuery("DELETE FROM Refund r WHERE r.booking.id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
                entityManager.createQuery("DELETE FROM PaymentAttempt pa WHERE pa.payment.booking.id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
                entityManager.createQuery("DELETE FROM Payment p WHERE p.booking.id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
                entityManager.createQuery("DELETE FROM OfflineCollection o WHERE o.booking.id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
                entityManager.createQuery("DELETE FROM HandoverEvidence he WHERE he.inspection.booking.id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
                entityManager.createQuery("DELETE FROM HandoverInspection hi WHERE hi.booking.id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
                entityManager.createQuery("DELETE FROM Commission c WHERE c.booking.id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
                entityManager.createQuery("DELETE FROM PartnerReceivable pr WHERE pr.booking.id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM security_deposit_records WHERE booking_id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM damage_reports WHERE booking_id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM reviews WHERE booking_id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM support_tickets WHERE booking_id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
                entityManager.createQuery("DELETE FROM BookingModification bm WHERE bm.booking.id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
                entityManager.createQuery("DELETE FROM BookingStatusHistory bh WHERE bh.booking.id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
                entityManager.createQuery("DELETE FROM Booking b WHERE b.id = :bid")
                        .setParameter("bid", b.getId()).executeUpdate();
            }

            List<String> actorKeys = allUserIds.stream()
                    .flatMap(id -> java.util.stream.Stream.of("usr:" + id, "user:" + id))
                    .toList();
            entityManager.createQuery("DELETE FROM IdempotencyRecord r WHERE r.actorKey IN :keys")
                    .setParameter("keys", actorKeys).executeUpdate();
            entityManager.createQuery("DELETE FROM Notification n WHERE n.user.id IN :uids")
                    .setParameter("uids", allUserIds).executeUpdate();
            entityManager.createQuery("DELETE FROM AuditLog a WHERE a.actorUser.id IN :uids")
                    .setParameter("uids", allUserIds).executeUpdate();

            entityManager.createNativeQuery("DELETE FROM damage_reports WHERE vehicle_id = :vid")
                    .setParameter("vid", fx.vehicleId()).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM reviews WHERE vehicle_id = :vid")
                    .setParameter("vid", fx.vehicleId()).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM pricing_rules WHERE vehicle_id = :vid")
                    .setParameter("vid", fx.vehicleId()).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM vehicle_pricing_configs WHERE vehicle_id = :vid")
                    .setParameter("vid", fx.vehicleId()).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM vehicle_status_history WHERE vehicle_id = :vid")
                    .setParameter("vid", fx.vehicleId()).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM vehicle_features WHERE vehicle_id = :vid")
                    .setParameter("vid", fx.vehicleId()).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM vehicle_documents WHERE vehicle_id = :vid")
                    .setParameter("vid", fx.vehicleId()).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM vehicle_images WHERE vehicle_id = :vid")
                    .setParameter("vid", fx.vehicleId()).executeUpdate();
            entityManager.createQuery("DELETE FROM InventoryHold h WHERE h.vehicle.id = :vid")
                    .setParameter("vid", fx.vehicleId()).executeUpdate();
            entityManager.createQuery("DELETE FROM VehicleUnavailability u WHERE u.vehicle.id = :vid")
                    .setParameter("vid", fx.vehicleId()).executeUpdate();
            entityManager.createQuery("DELETE FROM Vehicle v WHERE v.id = :vid")
                    .setParameter("vid", fx.vehicleId()).executeUpdate();
            entityManager.createQuery("DELETE FROM BusinessLocation bl WHERE bl.business.id = :bid")
                    .setParameter("bid", fx.bizId()).executeUpdate();
            entityManager.createQuery("DELETE FROM PartnerStaff ps WHERE ps.business.id = :bid")
                    .setParameter("bid", fx.bizId()).executeUpdate();
            entityManager.createQuery("DELETE FROM PartnerPolicy pp WHERE pp.business.id = :bid")
                    .setParameter("bid", fx.bizId()).executeUpdate();
            entityManager.createQuery("DELETE FROM PartnerOperatingHours poh WHERE poh.business.id = :bid")
                    .setParameter("bid", fx.bizId()).executeUpdate();
            entityManager.createQuery("DELETE FROM Business b WHERE b.id = :bid")
                    .setParameter("bid", fx.bizId()).executeUpdate();

            entityManager.createNativeQuery("DELETE FROM user_roles WHERE user_id IN :uids")
                    .setParameter("uids", allUserIds).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM admin_profiles WHERE user_id IN :uids")
                    .setParameter("uids", allUserIds).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM tourist_profiles WHERE user_id IN :uids")
                    .setParameter("uids", allUserIds).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM refresh_tokens WHERE user_id IN :uids")
                    .setParameter("uids", allUserIds).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM password_reset_tokens WHERE user_id IN :uids")
                    .setParameter("uids", allUserIds).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users WHERE id IN :uids")
                    .setParameter("uids", allUserIds).executeUpdate();

            entityManager.flush();
        });
    }

    private Fixture createFixture(BookingMode bookingMode, BigDecimal dailyRate, BigDecimal depositAmount) {
        return new TransactionTemplate(transactionManager).execute(s -> {
            String token = "BKG" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
            User owner = createUser("ROLE_PARTNER_OWNER", "own_" + token);
            User manager = createUser("ROLE_PARTNER_MANAGER", "mgr_" + token);
            User staff = createUser("ROLE_PARTNER_STAFF", "stf_" + token);
            User tourist = createUser("ROLE_TOURIST", "trst_" + token);
            User admin = createUser("ROLE_OPERATIONS_ADMIN", "adm_" + token);

            Island island = islandRepository.findByCode("PORT_BLAIR").orElseGet(() -> islandRepository.findAll().getFirst());
            Location loc = locationRepository.findByIslandIdAndActiveTrue(island.getId()).getFirst();
            VehicleCategory cat = categoryRepository.findAll().getFirst();

            Business biz = new Business();
            biz.setOwnerUser(owner);
            biz.setTradeName("Andaman Fleet " + token);
            biz.setLegalName("Andaman Fleet Pvt Ltd " + token);
            biz.setGstin("35AABCA1234F1Z" + (Math.abs(token.hashCode()) % 10));
            biz.setPanNumber("AABCA1234F");
            biz.setVerificationStatus(VerificationStatus.APPROVED);
            biz = businessRepository.save(biz);

            PartnerStaff ownerStaff = new PartnerStaff();
            ownerStaff.setBusiness(biz);
            ownerStaff.setUser(owner);
            ownerStaff.setRoleInBusiness("OWNER");
            ownerStaff.setActive(true);
            ownerStaff.setCanRecordHandover(true);
            ownerStaff.setCanCollectOfflinePayments(true);
            ownerStaff.setCanMarkNoShow(true);
            staffRepository.save(ownerStaff);

            PartnerStaff mgrStaff = new PartnerStaff();
            mgrStaff.setBusiness(biz);
            mgrStaff.setUser(manager);
            mgrStaff.setRoleInBusiness("MANAGER");
            mgrStaff.setActive(true);
            mgrStaff.setCanRecordHandover(true);
            mgrStaff.setCanCollectOfflinePayments(true);
            mgrStaff.setCanMarkNoShow(true);
            staffRepository.save(mgrStaff);

            PartnerStaff opStaff = new PartnerStaff();
            opStaff.setBusiness(biz);
            opStaff.setUser(staff);
            opStaff.setRoleInBusiness("STAFF");
            opStaff.setActive(true);
            opStaff.setCanRecordHandover(true);
            opStaff.setCanCollectOfflinePayments(true);
            opStaff.setCanMarkNoShow(true);
            staffRepository.save(opStaff);

            BusinessLocation bl = new BusinessLocation();
            bl.setId(new BusinessLocationId(biz.getId(), loc.getId()));
            bl.setBusiness(biz);
            bl.setLocation(loc);
            businessLocationRepository.save(bl);

            PartnerPolicy policy = new PartnerPolicy();
            policy.setBusiness(biz);
            policy.setTurnaroundBufferMinutes(60);
            policy.setDepositInspectionWindowHours(48);
            policy.setFullPaymentCutoffHours(24);
            policy.setAdvancePaymentPercentage(new BigDecimal("30.00"));
            partnerPolicyRepository.save(policy);

            Vehicle v = new Vehicle();
            v.setBusiness(biz);
            v.setIsland(island);
            v.setCategory(cat);
            v.setPickupLocation(loc);
            v.setReturnLocation(loc);
            v.setTitle("Royal Enfield Classic " + token);
            v.setMake("Royal Enfield");
            v.setModel("Classic 350");
            v.setModelYear(2025);
            v.setRegistrationNumber("AN01" + token.substring(0, 6));
            v.setDailyRate(dailyRate != null ? dailyRate : new BigDecimal("1200.00"));
            v.setSecurityDeposit(depositAmount != null ? depositAmount : new BigDecimal("2000.00"));
            v.setBookingMode(bookingMode != null ? bookingMode : BookingMode.INSTANT);
            v.setStatus(VehicleStatus.ACTIVE);
            v = vehicleRepository.save(v);

            Fixture f = new Fixture(owner.getId(), manager.getId(), staff.getId(), tourist.getId(), admin.getId(),
                    biz.getId(), v.getId(), v.getPublicId(), island.getId(), loc.getId(), loc.getId(), token,
                    new CopyOnWriteArrayList<>());
            fixturesToClean.add(f);
            return f;
        });
    }

    private User createUser(String roleName, String emailPrefix) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName(roleName);
                    r.setDescription("Test role " + roleName);
                    return roleRepository.save(r);
                });
        User user = new User();
        user.setEmail(emailPrefix.toLowerCase(Locale.ROOT) + "@islandride.test");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setFirstName(emailPrefix);
        user.setLastName("User");
        user.setPhone("+9198" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100000000));
        user.setStatus(UserStatus.ACTIVE);
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    private void authenticate(Long userId, Long businessId) {
        User user = new TransactionTemplate(transactionManager).execute(s -> {
            User u = userRepository.findById(userId).orElseThrow();
            u.getRoles().size();
            return u;
        });
        UserPrincipal principal = UserPrincipal.create(user, businessId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static Instant testTime(int plusDays, int hour) {
        LocalDate d = LocalDate.now(IST).plusDays(plusDays);
        return ZonedDateTime.of(d, LocalTime.of(hour, 0), IST).toInstant();
    }

    private String calculateHmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void publicBookingQuote_returnsAuthoritativePricingAndValidatesWindow() {
        Fixture fx = createFixture(BookingMode.INSTANT, new BigDecimal("1500.00"), new BigDecimal("3000.00"));
        Instant start = testTime(2, 10);
        Instant end = testTime(5, 10);

        BookingDtos.PricePreviewResponse quote = publicBookingQuoteService.calculatePublicQuote(
                fx.vehiclePublicId(),
                start,
                end,
                null,
                null,
                PaymentMode.FULL,
                false,
                Instant.now()
        );

        assertThat(quote).isNotNull();
        assertThat(quote.vehiclePublicId()).isEqualTo(fx.vehiclePublicId());
        assertThat(quote.available()).isTrue();
        assertThat(quote.billableDays()).isEqualTo(3);
        assertThat(quote.dailyRate()).isEqualByComparingTo("1500.00");
        assertThat(quote.securityDepositAmount()).isEqualByComparingTo("3000.00");
        assertThat(quote.depositCollectionMethod()).isEqualTo(SecurityDepositCollectionMethod.AT_PICKUP);
        assertThat(quote.totalPayableAmount()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void endToEndInstantBooking_withFullPayment_confirmsAndCaptures() {
        Fixture fx = createFixture(BookingMode.INSTANT, new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        authenticate(fx.touristUserId(), null);

        Instant start = testTime(3, 10);
        Instant end = testTime(5, 16);

        // 1. Acquire hold & fetch GET hold endpoint backing service
        InventoryHoldService.HoldResult hold = holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                fx.touristUserId(),
                fx.vehiclePublicId(),
                start,
                end,
                HoldPurpose.CHECKOUT,
                null,
                null,
                "idemp_hld_" + fx.token()
        ));
        assertThat(hold.status()).isEqualTo(HoldStatus.HELD);

        BookingDtos.InventoryHoldResponse holdDetail = bookingOrchestrationService.getTouristHold(fx.touristUserId(), hold.publicId());
        assertThat(holdDetail.publicId()).isEqualTo(hold.publicId());
        assertThat(holdDetail.fullQuote()).isNotNull();

        // 2. Create booking from hold
        BookingOrchestrationService.BookingCreationResult created = bookingOrchestrationService.createBookingFromHold(
                fx.touristUserId(),
                new BookingDtos.CreateBookingRequest(
                        hold.publicId(),
                        PaymentMode.FULL,
                        false,
                        "DL-AN01-2025-001928",
                        "+919876543210",
                        true,
                        "idemp_bkg_" + fx.token()
                )
        );

        assertThat(created.booking().summary().status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(created.checkoutOrder()).isNotNull();
        assertThat(created.checkoutOrder().gatewayOrderId()).isNotEmpty();

        // 3. Verify mock payment signature
        String mockPaymentId = "pay_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String mockSignature = calculateHmac(
                created.checkoutOrder().gatewayOrderId() + "|" + mockPaymentId,
                paymentGatewayProperties.getMock().getKeySecret()
        );

        PaymentDtos.PaymentVerificationResponse verified = paymentOrchestrationService.verifyClientPayment(
                fx.touristUserId(),
                new PaymentDtos.VerifyPaymentRequest(
                        created.booking().summary().publicId(),
                        created.checkoutOrder().gatewayOrderId(),
                        mockPaymentId,
                        mockSignature
                ),
                "idemp_ver_" + fx.token()
        );

        assertThat(verified.bookingStatus()).isEqualTo(BookingStatus.CONFIRMED.name());

        Booking reloaded = bookingRepository.findByPublicId(created.booking().summary().publicId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reloaded.getPaymentSummaryStatus()).isEqualTo(PaymentSummaryStatus.PAID_IN_FULL);
        assertThat(reloaded.getPickupBalanceDue()).isEqualByComparingTo("0.00");
    }

    @Test
    void endToEndPartialAdvance_pickupUpiBalanceAndReturn_calculatesCommissionExcludingDepositAndGST() {
        Fixture fx = createFixture(BookingMode.INSTANT, new BigDecimal("2000.00"), new BigDecimal("3000.00"));
        authenticate(fx.touristUserId(), null);

        Instant start = testTime(4, 10);
        Instant end = testTime(7, 16);

        InventoryHoldService.HoldResult hold = holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                fx.touristUserId(),
                fx.vehiclePublicId(),
                start,
                end,
                HoldPurpose.CHECKOUT,
                null,
                null,
                "hld_part_" + fx.token()
        ));

        BookingOrchestrationService.BookingCreationResult created = bookingOrchestrationService.createBookingFromHold(
                fx.touristUserId(),
                new BookingDtos.CreateBookingRequest(
                        hold.publicId(),
                        PaymentMode.PARTIAL_ADVANCE,
                        false,
                        "DL-AN01-2025-009988",
                        "+919876543211",
                        true,
                        "bkg_part_" + fx.token()
                )
        );

        assertThat(created.booking().summary().status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(created.booking().summary().pickupBalanceDue()).isGreaterThan(BigDecimal.ZERO);

        String mockPaymentId = "pay_part_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String mockSignature = calculateHmac(
                created.checkoutOrder().gatewayOrderId() + "|" + mockPaymentId,
                paymentGatewayProperties.getMock().getKeySecret()
        );

        paymentOrchestrationService.verifyClientPayment(
                fx.touristUserId(),
                new PaymentDtos.VerifyPaymentRequest(
                        created.booking().summary().publicId(),
                        created.checkoutOrder().gatewayOrderId(),
                        mockPaymentId,
                        mockSignature
                ),
                "ver_part_" + fx.token()
        );

        Booking confirmedBooking = bookingRepository.findByPublicId(created.booking().summary().publicId()).orElseThrow();
        assertThat(confirmedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmedBooking.getPaymentSummaryStatus()).isEqualTo(PaymentSummaryStatus.PARTIALLY_PAID);

        // Partner records pickup handover with UPI_QR balance + CASH deposit
        authenticate(fx.staffUserId(), fx.bizId());
        BookingDtos.BookingDetailDto activeDetail = bookingOrchestrationService.recordPickupHandover(
                fx.staffUserId(),
                confirmedBooking.getPublicId(),
                new BookingDtos.PickupHandoverRequest(
                        12050,
                        95,
                        "{}",
                        "Vehicle handed over in pristine condition",
                        confirmedBooking.getPickupBalanceDue(),
                        "UPI_QR",
                        "RCP-BAL-" + fx.token(),
                        new BigDecimal("3000.00"),
                        "CASH",
                        "RCP-DEP-" + fx.token(),
                        false,
                        List.of(new BookingDtos.HandoverEvidenceInput("ODOMETER", "s3/evidence/odo1.jpg", "image/jpeg"))
                )
        );

        assertThat(activeDetail.summary().status()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(activeDetail.summary().pickupBalanceDue()).isEqualByComparingTo("0.00");
        assertThat(activeDetail.summary().paymentSummaryStatus()).isEqualTo(PaymentSummaryStatus.PAID_IN_FULL);

        // Verify offline UPI balance mapped correctly to OFFLINE_UPI
        List<Payment> bookingPayments = paymentRepository.findByBookingIdOrderByCreatedAtDesc(confirmedBooking.getId());
        assertThat(bookingPayments).anyMatch(p -> p.getPaymentType() == PaymentType.PICKUP_BALANCE
                && p.getPaymentMethod() == PaymentMethod.OFFLINE_UPI);

        // Duplicate pickup handover rejected
        assertThatThrownBy(() -> bookingOrchestrationService.recordPickupHandover(
                fx.staffUserId(),
                confirmedBooking.getPublicId(),
                new BookingDtos.PickupHandoverRequest(
                        12055, 95, "{}", "duplicate attempt", null, null, null, null, null, null, false, List.of()
                )))
                .isInstanceOf(ConflictException.class);

        // Return completion: verify odometer regression rejected
        assertThatThrownBy(() -> bookingOrchestrationService.recordReturnCompletion(
                fx.staffUserId(),
                confirmedBooking.getPublicId(),
                new BookingDtos.ReturnCompleteRequest(
                        12000,
                        90,
                        "{}",
                        "Odometer rolled back",
                        false,
                        BigDecimal.ZERO,
                        true,
                        List.of()
                )))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("less than pickup odometer");

        // Successful return completion: deposit released, commission generated
        BookingDtos.BookingDetailDto completedDetail = bookingOrchestrationService.recordReturnCompletion(
                fx.staffUserId(),
                confirmedBooking.getPublicId(),
                new BookingDtos.ReturnCompleteRequest(
                        12450,
                        90,
                        "{}",
                        "Returned cleanly without damage",
                        false,
                        BigDecimal.ZERO,
                        true,
                        List.of(new BookingDtos.HandoverEvidenceInput("SIGNATURE", "s3/evidence/sig.jpg", "image/jpeg"))
                )
        );

        assertThat(completedDetail.summary().status()).isEqualTo(BookingStatus.COMPLETED);

        Commission comm = commissionRepository.findByBookingId(confirmedBooking.getId()).orElseThrow();
        assertThat(comm.getStatus()).isEqualTo(CommissionStatus.ACCRUED);
        assertThat(comm.getCommissionAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(comm.getTaxExcludedAmount()).isLessThan(comm.getGrossBookingAmount());
    }

    @Test
    void partnerApprovalWorkflow_approvalRejectAndPostApprovalPayment() {
        Fixture fx = createFixture(BookingMode.APPROVAL, new BigDecimal("1800.00"), new BigDecimal("2500.00"));
        authenticate(fx.touristUserId(), null);

        Instant start = testTime(5, 10);
        Instant end = testTime(7, 16);

        InventoryHoldService.HoldResult hold = holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                fx.touristUserId(),
                fx.vehiclePublicId(),
                start,
                end,
                HoldPurpose.CHECKOUT,
                null,
                null,
                "hld_appr_" + fx.token()
        ));

        BookingOrchestrationService.BookingCreationResult created = bookingOrchestrationService.createBookingFromHold(
                fx.touristUserId(),
                new BookingDtos.CreateBookingRequest(
                        hold.publicId(),
                        PaymentMode.FULL,
                        false,
                        "DL-AN01-2025-007788",
                        "+919876543212",
                        true,
                        "bkg_appr_" + fx.token()
                )
        );

        assertThat(created.booking().summary().status()).isEqualTo(BookingStatus.AWAITING_PARTNER_APPROVAL);
        assertThat(created.checkoutOrder()).isNull();

        authenticate(fx.managerUserId(), fx.bizId());
        BookingDtos.BookingDetailDto approved = bookingOrchestrationService.partnerApproveBooking(
                fx.managerUserId(),
                created.booking().summary().publicId(),
                new BookingDtos.PartnerApprovalDecisionRequest("Vehicle available and inspected for your dates")
        );

        assertThat(approved.summary().status()).isEqualTo(BookingStatus.APPROVED);
        assertThat(approved.summary().paymentDeadlineAt()).isNotNull();

        authenticate(fx.touristUserId(), null);
        PaymentDtos.GatewayCheckoutOrderResponse order = paymentOrchestrationService.initiateOrRetryCheckoutOrder(
                approved.summary().publicId(),
                fx.touristUserId(),
                "order_post_appr_" + fx.token()
        );
        assertThat(order.gatewayOrderId()).isNotEmpty();

        // Test rejection path on separate booking
        InventoryHoldService.HoldResult hold2 = holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                fx.touristUserId(),
                fx.vehiclePublicId(),
                start.plus(Duration.ofDays(4)),
                end.plus(Duration.ofDays(4)),
                HoldPurpose.CHECKOUT,
                null,
                null,
                "hld_rej_" + fx.token()
        ));
        BookingOrchestrationService.BookingCreationResult created2 = bookingOrchestrationService.createBookingFromHold(
                fx.touristUserId(),
                new BookingDtos.CreateBookingRequest(
                        hold2.publicId(), PaymentMode.FULL, false, "DL-AN01-2025-007789", "+919876543213", true, "bkg_rej_" + fx.token()
                )
        );

        authenticate(fx.managerUserId(), fx.bizId());
        BookingDtos.BookingDetailDto rejected = bookingOrchestrationService.partnerRejectBooking(
                fx.managerUserId(),
                created2.booking().summary().publicId(),
                new BookingDtos.PartnerApprovalDecisionRequest("Scheduled workshop servicing")
        );
        assertThat(rejected.summary().status()).isEqualTo(BookingStatus.REJECTED);
    }

    @Test
    void highConcurrency50Threads_onlyOneHoldSucceedsOnSameVehicleOverlappingDates() throws Exception {
        Fixture fx = createFixture(BookingMode.INSTANT, new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Instant start = testTime(7, 10);
        Instant end = testTime(9, 16);

        List<User> tourists = new ArrayList<>();
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            for (int i = 0; i < threadCount; i++) {
                User u = createUser("ROLE_TOURIST", "racetr_" + i + "_" + fx.token());
                tourists.add(u);
                fx.extraUserIds().add(u.getId());
            }
        });

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                            tourists.get(index).getId(),
                            fx.vehiclePublicId(),
                            start,
                            end,
                            HoldPurpose.CHECKOUT,
                            null,
                            null,
                            "hld_race_" + index + "_" + fx.token()
                    ));
                    successCount.incrementAndGet();
                } catch (ConflictException ex) {
                    conflictCount.incrementAndGet();
                } catch (Exception ex) {
                    // ignore
                }
            });
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
    }

    @Test
    void idempotencyReplay_replaysMatchingPayload_andRejectsPayloadMismatch() {
        Fixture fx = createFixture(BookingMode.INSTANT, new BigDecimal("1100.00"), new BigDecimal("2200.00"));
        authenticate(fx.touristUserId(), null);

        Instant start = testTime(10, 10);
        Instant end = testTime(12, 16);

        InventoryHoldService.HoldResult hold = holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                fx.touristUserId(),
                fx.vehiclePublicId(),
                start,
                end,
                HoldPurpose.CHECKOUT,
                null,
                null,
                "idemp_test_hold_" + fx.token()
        ));

        String idempKey = "bkg_idemp_key_" + fx.token();
        var req1 = new BookingDtos.CreateBookingRequest(
                hold.publicId(),
                PaymentMode.FULL,
                false,
                "DL-AN01-2025-111111",
                "+919876543222",
                true,
                idempKey
        );

        BookingOrchestrationService.BookingCreationResult first = bookingOrchestrationService.createBookingFromHold(fx.touristUserId(), req1);
        assertThat(first).isNotNull();

        BookingOrchestrationService.BookingCreationResult replayed = bookingOrchestrationService.createBookingFromHold(fx.touristUserId(), req1);
        assertThat(replayed.booking().summary().publicId()).isEqualTo(first.booking().summary().publicId());

        var req2WithDifferentPhone = new BookingDtos.CreateBookingRequest(
                hold.publicId(),
                PaymentMode.FULL,
                false,
                "DL-AN01-2025-111111",
                "+919876543999",
                true,
                idempKey
        );

        assertThatThrownBy(() -> bookingOrchestrationService.createBookingFromHold(fx.touristUserId(), req2WithDifferentPhone))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void webhookSignatureTampering_rejected_andValidWebhookCapturesPayment() throws Exception {
        Fixture fx = createFixture(BookingMode.INSTANT, new BigDecimal("1200.00"), new BigDecimal("2000.00"));
        authenticate(fx.touristUserId(), null);

        Instant start = testTime(12, 10);
        Instant end = testTime(14, 16);

        InventoryHoldService.HoldResult hold = holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                fx.touristUserId(),
                fx.vehiclePublicId(),
                start,
                end,
                HoldPurpose.CHECKOUT,
                null,
                null,
                "hld_whk_" + fx.token()
        ));

        BookingOrchestrationService.BookingCreationResult created = bookingOrchestrationService.createBookingFromHold(
                fx.touristUserId(),
                new BookingDtos.CreateBookingRequest(
                        hold.publicId(),
                        PaymentMode.FULL,
                        false,
                        "DL-AN01-2025-333333",
                        "+919876543333",
                        true,
                        "bkg_whk_" + fx.token()
                )
        );

        String orderId = created.checkoutOrder().gatewayOrderId();
        String paymentId = "pay_whk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        Map<String, Object> payloadMap = Map.of(
                "event", "payment.captured",
                "id", eventId,
                "payload", Map.of(
                        "payment", Map.of(
                                "entity", Map.of(
                                        "id", paymentId,
                                        "order_id", orderId,
                                        "status", "captured",
                                        "amount", 283200,
                                        "currency", "INR"
                                )
                        )
                )
        );
        String rawBody = objectMapper.writeValueAsString(payloadMap);

        // 1. Invalid signature rejected with SecurityException
        assertThatThrownBy(() -> webhookProcessingService
                .processRawWebhook("mock", rawBody, "invalid_signature", eventId))
                .isInstanceOf(SecurityException.class);

        // 2. Valid signature accepted & booking confirmed
        String validSig = calculateHmac(rawBody, paymentGatewayProperties.getMock().getWebhookSecret());
        WebhookProcessingService.WebhookHandleOutcome goodOutcome = webhookProcessingService
                .processRawWebhook("mock", rawBody, validSig, eventId);
        assertThat(goodOutcome.accepted()).isTrue();
        assertThat(goodOutcome.signatureValid()).isTrue();
        assertThat(goodOutcome.duplicate()).isFalse();

        Booking confirmed = bookingRepository.findByPublicId(created.booking().summary().publicId()).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // 3. Duplicate eventId idempotently ignored
        WebhookProcessingService.WebhookHandleOutcome dupOutcome = webhookProcessingService
                .processRawWebhook("mock", rawBody, validSig, eventId);
        assertThat(dupOutcome.accepted()).isTrue();
        assertThat(dupOutcome.duplicate()).isTrue();
    }

    @Test
    void latePaymentCapture_whenSlotTaken_cancelsBookingAndRecordsOverbookingExceptionWithRefund() {
        Fixture fx = createFixture(BookingMode.INSTANT, new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        authenticate(fx.touristUserId(), null);

        Instant start = testTime(14, 10);
        Instant end = testTime(16, 16);

        InventoryHoldService.HoldResult hold = holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                fx.touristUserId(),
                fx.vehiclePublicId(),
                start,
                end,
                HoldPurpose.CHECKOUT,
                null,
                null,
                "hld_late_" + fx.token()
        ));

        BookingOrchestrationService.BookingCreationResult lateBooking = bookingOrchestrationService.createBookingFromHold(
                fx.touristUserId(),
                new BookingDtos.CreateBookingRequest(
                        hold.publicId(),
                        PaymentMode.FULL,
                        false,
                        "DL-AN01-2025-444444",
                        "+919876543444",
                        true,
                        "bkg_late_" + fx.token()
                )
        );

        // Mark the late booking EXPIRED
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            Booking b = bookingRepository.findByPublicId(lateBooking.booking().summary().publicId()).orElseThrow();
            b.setStatus(BookingStatus.EXPIRED);
            b.setPaymentDeadlineAt(Instant.now().minus(Duration.ofMinutes(10)));
            bookingRepository.save(b);
        });

        // Another tourist claims the slot
        User anotherTourist = new TransactionTemplate(transactionManager).execute(s -> {
            User u = createUser("ROLE_TOURIST", "secondtr_" + fx.token());
            fx.extraUserIds().add(u.getId());
            return u;
        });
        authenticate(anotherTourist.getId(), null);
        InventoryHoldService.HoldResult hold2 = holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                anotherTourist.getId(),
                fx.vehiclePublicId(),
                start,
                end,
                HoldPurpose.CHECKOUT,
                null,
                null,
                "hld_second_" + fx.token()
        ));
        bookingOrchestrationService.createBookingFromHold(
                anotherTourist.getId(),
                new BookingDtos.CreateBookingRequest(
                        hold2.publicId(),
                        PaymentMode.FULL,
                        false,
                        "DL-AN01-2025-555555",
                        "+919876543555",
                        true,
                        "bkg_second_" + fx.token()
                )
        );

        // Now late payment capture arrives for the first booking via webhook!
        String latePayId = "pay_late_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        paymentOrchestrationService.processWebhookCapture(
                "mock",
                lateBooking.checkoutOrder().gatewayOrderId(),
                latePayId,
                Instant.now()
        );

        // First booking transitions EXPIRED -> CANCELLED with OverbookingExceptionRecord + automated refund
        Booking reloadedLateBooking = bookingRepository.findByPublicId(lateBooking.booking().summary().publicId()).orElseThrow();
        assertThat(reloadedLateBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(reloadedLateBooking.getOverbookingExceptionStatus()).isEqualTo(OverbookingExceptionStatus.REFUND_INITIATED);

        OverbookingExceptionRecord obRecord = overbookingRepository.findByBookingId(reloadedLateBooking.getId()).orElseThrow();
        assertThat(obRecord.getStatus()).isEqualTo(OverbookingExceptionStatus.REFUND_INITIATED);

        // Admin resolves overbooking exception
        authenticate(fx.adminUserId(), null);
        BookingDtos.OverbookingExceptionDto resolved = bookingOrchestrationService.resolveOverbookingException(
                obRecord.getPublicId(),
                new BookingDtos.ResolveOverbookingRequest("Contacted tourist and confirmed 100% refund settlement"),
                fx.adminUserId()
        );
        assertThat(resolved.status()).isEqualTo(OverbookingExceptionStatus.RESOLVED.name());
    }

    @Test
    void bookingModification_selfExcludesAndAdjustsPricing() {
        Fixture fx = createFixture(BookingMode.INSTANT, new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        authenticate(fx.touristUserId(), null);

        Instant start = testTime(20, 10);
        Instant end = testTime(22, 16);

        InventoryHoldService.HoldResult hold = holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                fx.touristUserId(),
                fx.vehiclePublicId(),
                start,
                end,
                HoldPurpose.CHECKOUT,
                null,
                null,
                "hld_mod_" + fx.token()
        ));

        BookingOrchestrationService.BookingCreationResult created = bookingOrchestrationService.createBookingFromHold(
                fx.touristUserId(),
                new BookingDtos.CreateBookingRequest(
                        hold.publicId(),
                        PaymentMode.FULL,
                        false,
                        "DL-AN01-2025-666666",
                        "+919876543666",
                        true,
                        "bkg_mod_" + fx.token()
                )
        );

        String mockPaymentId = "pay_mod_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String mockSignature = calculateHmac(
                created.checkoutOrder().gatewayOrderId() + "|" + mockPaymentId,
                paymentGatewayProperties.getMock().getKeySecret()
        );
        paymentOrchestrationService.verifyClientPayment(
                fx.touristUserId(),
                new PaymentDtos.VerifyPaymentRequest(created.booking().summary().publicId(), created.checkoutOrder().gatewayOrderId(), mockPaymentId, mockSignature),
                "ver_mod_" + fx.token()
        );

        Instant newStart = start.plus(Duration.ofDays(1));
        Instant newEnd = end.plus(Duration.ofDays(1));

        BookingDtos.BookingDetailDto modified = bookingOrchestrationService.modifyBooking(
                fx.touristUserId(),
                created.booking().summary().publicId(),
                new BookingDtos.ModifyBookingRequest(newStart, newEnd, null, null, false, "Shift dates forward by 1 day")
        );

        assertThat(modified.summary().startTime()).isEqualTo(newStart);
        assertThat(modified.summary().endTime()).isEqualTo(newEnd);
        assertThat(modified.modifications()).hasSize(1);
    }

    @Test
    void cancellationPreviewExecutionAndDisruptionOverride_withDepositProtection() {
        Fixture fx = createFixture(BookingMode.INSTANT, new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        authenticate(fx.touristUserId(), null);

        Instant start = testTime(25, 10);
        Instant end = testTime(28, 16);

        InventoryHoldService.HoldResult hold = holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                fx.touristUserId(),
                fx.vehiclePublicId(),
                start,
                end,
                HoldPurpose.CHECKOUT,
                null,
                null,
                "hld_cxl_" + fx.token()
        ));

        BookingOrchestrationService.BookingCreationResult created = bookingOrchestrationService.createBookingFromHold(
                fx.touristUserId(),
                new BookingDtos.CreateBookingRequest(
                        hold.publicId(),
                        PaymentMode.FULL,
                        false,
                        "DL-AN01-2025-777777",
                        "+919876543777",
                        true,
                        "bkg_cxl_" + fx.token()
                )
        );

        String mockPaymentId = "pay_cxl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String mockSignature = calculateHmac(
                created.checkoutOrder().gatewayOrderId() + "|" + mockPaymentId,
                paymentGatewayProperties.getMock().getKeySecret()
        );
        paymentOrchestrationService.verifyClientPayment(
                fx.touristUserId(),
                new PaymentDtos.VerifyPaymentRequest(created.booking().summary().publicId(), created.checkoutOrder().gatewayOrderId(), mockPaymentId, mockSignature),
                "ver_cxl_" + fx.token()
        );

        BookingDtos.CancellationPreviewResponse preview = bookingOrchestrationService.previewCancellation(
                fx.touristUserId(),
                created.booking().summary().publicId()
        );
        assertThat(preview.depositProtected()).isTrue();
        assertThat(preview.totalRefundAmount()).isGreaterThan(BigDecimal.ZERO);

        BookingDtos.BookingDetailDto cancelled = bookingOrchestrationService.cancelBookingByTourist(
                fx.touristUserId(),
                created.booking().summary().publicId(),
                new BookingDtos.CancelBookingRequest("Change of travel plans")
        );
        assertThat(cancelled.summary().status()).isEqualTo(BookingStatus.CANCELLED);

        // Second booking for Admin Disruption Override (weather ferry)
        InventoryHoldService.HoldResult hold2 = holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                fx.touristUserId(),
                fx.vehiclePublicId(),
                start.plus(Duration.ofDays(5)),
                end.plus(Duration.ofDays(5)),
                HoldPurpose.CHECKOUT,
                null,
                null,
                "hld_disr_" + fx.token()
        ));
        BookingOrchestrationService.BookingCreationResult created2 = bookingOrchestrationService.createBookingFromHold(
                fx.touristUserId(),
                new BookingDtos.CreateBookingRequest(
                        hold2.publicId(), PaymentMode.FULL, false, "DL-AN01-2025-777778", "+919876543778", true, "bkg_disr_" + fx.token()
                )
        );
        String mockPay2 = "pay_disr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String mockSig2 = calculateHmac(created2.checkoutOrder().gatewayOrderId() + "|" + mockPay2, paymentGatewayProperties.getMock().getKeySecret());
        paymentOrchestrationService.verifyClientPayment(
                fx.touristUserId(),
                new PaymentDtos.VerifyPaymentRequest(created2.booking().summary().publicId(), created2.checkoutOrder().gatewayOrderId(), mockPay2, mockSig2),
                "ver_disr_" + fx.token()
        );

        authenticate(fx.adminUserId(), null);
        BookingDtos.BookingDetailDto disrupted = bookingOrchestrationService.adminDisruptionOverride(
                created2.booking().summary().publicId(),
                new BookingDtos.DisruptionOverrideRequest(DisruptionExceptionType.WEATHER_DISRUPTION, "Cyclone warning canceled Makruzz ferry", false),
                fx.adminUserId()
        );
        assertThat(disrupted.summary().status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(disrupted.disruptionExceptionType()).isEqualTo(DisruptionExceptionType.WEATHER_DISRUPTION);
    }

    @Test
    void tenantSecurityIsolation_andAdminFinanceOverview_andSchedulersExecuteCleanly() {
        Fixture fxA = createFixture(BookingMode.INSTANT, new BigDecimal("1000.00"), new BigDecimal("2000.00"));
        Fixture fxB = createFixture(BookingMode.INSTANT, new BigDecimal("1000.00"), new BigDecimal("2000.00"));

        authenticate(fxB.staffUserId(), fxB.bizId());

        assertThatThrownBy(() -> tenantSecurityService.requireBusinessReadAccess(fxA.bizId()))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> tenantSecurityService.requireHandoverAccess(fxA.bizId()))
                .isInstanceOf(AccessDeniedException.class);

        PaymentDtos.AdminFinanceOverviewDto overview = adminFinanceOverviewService.getFinanceOverview();
        assertThat(overview).isNotNull();
        assertThat(overview.depositTrustSummary()).isNotNull();
        assertThat(overview.depositTrustSummary().activeHeldCount()).isGreaterThanOrEqualTo(0);

        // Verify background schedulers execute cleanly on demand
        new BookingExpirationScheduler(bookingOrchestrationService).runBookingExpirationSweep();
        new RefundRetryScheduler(refundOrchestrationService).runRefundRetrySweep();
        new PaymentReconciliationScheduler(paymentOrchestrationService).runPaymentReconciliationSweep();
    }
}
