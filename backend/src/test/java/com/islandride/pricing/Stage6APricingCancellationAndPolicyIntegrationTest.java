package com.islandride.pricing;

import com.islandride.booking.domain.*;
import com.islandride.booking.repository.BookingRepository;
import com.islandride.booking.service.CancellationPolicySnapshot;
import com.islandride.booking.service.CancellationSettlementBreakdown;
import com.islandride.booking.service.CancellationSnapshotService;
import com.islandride.commission.domain.Commission;
import com.islandride.commission.service.CommissionCalculationService;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.geography.domain.Island;
import com.islandride.geography.domain.Location;
import com.islandride.geography.repository.IslandRepository;
import com.islandride.geography.repository.LocationRepository;
import com.islandride.identityaccess.domain.Role;
import com.islandride.identityaccess.domain.User;
import com.islandride.identityaccess.domain.UserStatus;
import com.islandride.identityaccess.repository.RoleRepository;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.inventory.domain.*;
import com.islandride.inventory.repository.VehicleCategoryRepository;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.partner.repository.BusinessRepository;
import com.islandride.paymentfinance.domain.PartnerReceivable;
import com.islandride.paymentfinance.domain.ReceivableStatus;
import com.islandride.paymentfinance.repository.PartnerReceivableRepository;
import com.islandride.pricing.domain.*;
import com.islandride.pricing.repository.*;
import com.islandride.pricing.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class Stage6APricingCancellationAndPolicyIntegrationTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private AuthoritativePricingService authoritativePricingService;
    @Autowired
    private BookingPolicyService bookingPolicyService;
    @Autowired
    private CancellationSnapshotService cancellationSnapshotService;
    @Autowired
    private CommissionCalculationService commissionCalculationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private IslandRepository islandRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private VehicleCategoryRepository categoryRepository;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private VehiclePricingPlanRepository pricingPlanRepository;
    @Autowired
    private SeasonalPriceRepository seasonalPriceRepository;
    @Autowired
    private PricingRuleRepository pricingRuleRepository;
    @Autowired
    private PartnerPolicyRepository partnerPolicyRepository;
    @Autowired
    private PartnerCancellationTierRepository cancellationTierRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private PartnerReceivableRepository receivableRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void authoritativePricing_handlesHourlyWeeklySeasonsPriorityExclusiveRulesAndExcludesDepositFromTaxAndCommission() {
        TestEntities fx = createBaseEntities();

        VehiclePricingPlan plan = new VehiclePricingPlan();
        plan.setVehicle(fx.vehicle());
        plan.setCurrency("INR");
        plan.setHourlyRate(new BigDecimal("120.00"));
        plan.setDailyRate(new BigDecimal("1000.00"));
        plan.setWeeklyRate(new BigDecimal("6000.00"));
        plan.setSecurityDeposit(new BigDecimal("3000.00"));
        plan.setCleaningFee(new BigDecimal("200.00"));
        plan.setDeliveryFee(new BigDecimal("150.00"));
        plan.setMinRentalHours(4);
        plan.setMaxRentalDays(30);
        plan.setActive(true);
        pricingPlanRepository.save(plan);

        // Minimum rental hours check (< 4h rejected)
        Instant quoteAt = ZonedDateTime.of(LocalDate.now(IST).plusDays(2), LocalTime.of(8, 0), IST).toInstant();
        assertThatThrownBy(() -> authoritativePricingService.calculateQuote(
                fx.vehicle(),
                quoteAt.plus(1, ChronoUnit.HOURS),
                quoteAt.plus(3, ChronoUnit.HOURS),
                PaymentMode.FULL,
                false,
                quoteAt))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Minimum rental duration");

        // Hourly billing (5h < 24h)
        Instant hourlyStart = ZonedDateTime.of(LocalDate.now(IST).plusDays(5), LocalTime.of(9, 0), IST).toInstant();
        PricingSnapshot hourlyQuote = authoritativePricingService.calculateQuote(
                fx.vehicle(),
                hourlyStart,
                hourlyStart.plus(5, ChronoUnit.HOURS),
                PaymentMode.PARTIAL_ADVANCE,
                false,
                quoteAt
        );
        assertThat(hourlyQuote.billingUnit()).isEqualTo("HOURLY");
        assertThat(hourlyQuote.billableHours()).isEqualTo(5);
        assertThat(hourlyQuote.baseRentalAmount()).isEqualByComparingTo("600.00");

        // Multi-day seasonal + exclusive group + stackable rules test (10 days -> 1 week + 3 days = 6000 + 3000 = 9000)
        LocalDate startLocal = LocalDate.now(IST).plusDays(10);
        Instant multiStart = ZonedDateTime.of(startLocal, LocalTime.of(10, 0), IST).toInstant();
        Instant multiEnd = ZonedDateTime.of(startLocal.plusDays(10), LocalTime.of(10, 0), IST).toInstant();

        // Seasonal 1.20x for the first 5 days only -> average multiplier over 10 days = (5 * 1.20 + 5 * 1.00) / 10 = 1.10
        SeasonalPrice season = new SeasonalPrice();
        season.setIsland(fx.island());
        season.setCategory(fx.category());
        season.setSeasonName("Peak Test Season");
        season.setStartDate(startLocal);
        season.setEndDate(startLocal.plusDays(4));
        season.setMultiplier(new BigDecimal("1.20"));
        season.setActive(true);
        seasonalPriceRepository.save(season);

        // Two rules in same exclusive group ("PROMO"): priority 10 (-10%) must win over priority 20 (-25%)
        PricingRule promoPriority10 = new PricingRule();
        promoPriority10.setVehicle(fx.vehicle());
        promoPriority10.setBusiness(fx.business());
        promoPriority10.setCategory(fx.category());
        promoPriority10.setRuleName("Long Stay 10%");
        promoPriority10.setRuleType("DURATION_DISCOUNT");
        promoPriority10.setMinDays(7);
        promoPriority10.setAdjustmentType("PERCENTAGE");
        promoPriority10.setAdjustmentValue(new BigDecimal("-10.00"));
        promoPriority10.setPriority(10);
        promoPriority10.setStackable(true);
        promoPriority10.setExclusiveGroup("PROMO");
        promoPriority10.setActive(true);
        pricingRuleRepository.save(promoPriority10);

        PricingRule promoPriority20 = new PricingRule();
        promoPriority20.setVehicle(fx.vehicle());
        promoPriority20.setBusiness(fx.business());
        promoPriority20.setCategory(fx.category());
        promoPriority20.setRuleName("Superseded 25%");
        promoPriority20.setRuleType("DURATION_DISCOUNT");
        promoPriority20.setMinDays(7);
        promoPriority20.setAdjustmentType("PERCENTAGE");
        promoPriority20.setAdjustmentValue(new BigDecimal("-25.00"));
        promoPriority20.setPriority(20);
        promoPriority20.setStackable(true);
        promoPriority20.setExclusiveGroup("PROMO");
        promoPriority20.setActive(true);
        pricingRuleRepository.save(promoPriority20);

        // Stackable surcharge rule priority 30 (+500 fixed)
        PricingRule stackableSurcharge = new PricingRule();
        stackableSurcharge.setVehicle(fx.vehicle());
        stackableSurcharge.setBusiness(fx.business());
        stackableSurcharge.setCategory(fx.category());
        stackableSurcharge.setRuleName("Island Peak Ops Fee");
        stackableSurcharge.setRuleType("EARLY_BIRD");
        stackableSurcharge.setMinDays(1);
        stackableSurcharge.setAdjustmentType("FIXED_AMOUNT");
        stackableSurcharge.setAdjustmentValue(new BigDecimal("500.00"));
        stackableSurcharge.setPriority(30);
        stackableSurcharge.setStackable(true);
        stackableSurcharge.setActive(true);
        pricingRuleRepository.save(stackableSurcharge);

        PricingSnapshot weeklyQuote = authoritativePricingService.calculateQuote(
                fx.vehicle(), multiStart, multiEnd, PaymentMode.PARTIAL_ADVANCE, true, quoteAt
        );

        assertThat(weeklyQuote.billingUnit()).isEqualTo("WEEKLY_PLUS_DAILY");
        assertThat(weeklyQuote.billableWeeks()).isEqualTo(1);
        assertThat(weeklyQuote.remainderDays()).isEqualTo(3);
        assertThat(weeklyQuote.baseRentalAmount()).isEqualByComparingTo("9000.00");
        assertThat(weeklyQuote.seasonalMultiplier()).isEqualByComparingTo("1.10");
        assertThat(weeklyQuote.seasonalAdjustmentAmount()).isEqualByComparingTo("900.00"); // 9000 * 1.10 = 9900 -> +900
        assertThat(weeklyQuote.appliedRules()).hasSize(2); // promoPriority10 + stackableSurcharge; promoPriority20 skipped
        assertThat(weeklyQuote.discountAmount()).isEqualByComparingTo("990.00"); // 10% of 9900
        assertThat(weeklyQuote.surchargeAmount()).isEqualByComparingTo("500.00");
        assertThat(weeklyQuote.adjustedRentalSubtotal()).isEqualByComparingTo("9410.00"); // 9900 - 990 + 500
        assertThat(weeklyQuote.commissionableRentalBase()).isEqualByComparingTo("9760.00"); // 9410 + 200 cleaning + 150 delivery
        // 5% GST strictly on 9760 (security deposit 3000 excluded!)
        assertThat(weeklyQuote.taxAmount()).isEqualByComparingTo("488.00");
        assertThat(weeklyQuote.totalPayableAmount()).isEqualByComparingTo("10248.00");
        assertThat(weeklyQuote.securityDepositAmount()).isEqualByComparingTo("3000.00");
        assertThat(weeklyQuote.enforcedPaymentMode()).isEqualTo(PaymentMode.PARTIAL_ADVANCE);
        assertThat(weeklyQuote.advancePaidDueNow()).isEqualByComparingTo("2562.00"); // 25% of 10248
        assertThat(weeklyQuote.pickupBalanceDue()).isEqualByComparingTo("7686.00");
        // 4% commission strictly excluding GST (488) and deposit (3000) -> 4% of 9760 = 390.40
        assertThat(weeklyQuote.estimatedCommissionAmount()).isEqualByComparingTo("390.40");
        assertThat(authoritativePricingService.serialize(weeklyQuote)).contains("\"billingUnit\":\"WEEKLY_PLUS_DAILY\"");
    }

    @Test
    void bookingPolicy_resolvesV8ApprovalDeadlinesPostApprovalPaymentWindowsAndFullPaymentCutoff() {
        TestEntities fx = createBaseEntities();

        PartnerPolicy custom = new PartnerPolicy();
        custom.setBusiness(fx.business());
        custom.setAdvancePaymentPercentage(new BigDecimal("30.00"));
        custom.setFullPaymentCutoffHours(18);
        custom.setTurnaroundBufferMinutes(90);
        custom.setSecurityDepositCollectionMethod(SecurityDepositCollectionMethod.ONLINE);
        custom.setNoShowGraceMinutes(90);
        custom.setNoShowPenaltyPercentage(new BigDecimal("100.00"));
        partnerPolicyRepository.save(custom);

        EffectiveBookingPolicy policy = bookingPolicyService.resolvePolicy(fx.business().getId());
        assertThat(policy.advancePaymentPercentage()).isEqualByComparingTo("30.00");
        assertThat(policy.fullPaymentCutoffHours()).isEqualTo(18);
        assertThat(policy.turnaroundBufferMinutes()).isEqualTo(90);
        assertThat(policy.depositCollectionMethod()).isEqualTo(SecurityDepositCollectionMethod.ONLINE);

        Instant now = Instant.now();
        // >24h -> 6h approval window
        assertThat(policy.computeApprovalExpiresAt(now, now.plus(30, ChronoUnit.HOURS)))
                .isEqualTo(now.plus(6, ChronoUnit.HOURS));
        // 6-24h -> 2h approval window
        assertThat(policy.computeApprovalExpiresAt(now, now.plus(10, ChronoUnit.HOURS)))
                .isEqualTo(now.plus(2, ChronoUnit.HOURS));
        // <6h -> 45m approval window
        assertThat(policy.computeApprovalExpiresAt(now, now.plus(3, ChronoUnit.HOURS)))
                .isEqualTo(now.plus(45, ChronoUnit.MINUTES));

        // Post-approval payment window: >=6h -> 2h, <6h -> 30m
        assertThat(policy.computePostApprovalPaymentDeadline(now, now.plus(10, ChronoUnit.HOURS)))
                .isEqualTo(now.plus(2, ChronoUnit.HOURS));
        assertThat(policy.computePostApprovalPaymentDeadline(now, now.plus(4, ChronoUnit.HOURS)))
                .isEqualTo(now.plus(30, ChronoUnit.MINUTES));

        // Request starting inside 18h cutoff forces FULL payment even if PARTIAL_ADVANCE was requested
        Instant nearPickup = ZonedDateTime.of(LocalDate.now(IST).plusDays(1), LocalTime.of(10, 0), IST).toInstant();
        Instant quoteInsideCutoff = nearPickup.minus(12, ChronoUnit.HOURS);
        PricingSnapshot forcedFull = authoritativePricingService.calculateQuote(
                fx.vehicle(), nearPickup, nearPickup.plus(8, ChronoUnit.HOURS),
                PaymentMode.PARTIAL_ADVANCE, false, quoteInsideCutoff
        );
        assertThat(forcedFull.requestedPaymentMode()).isEqualTo(PaymentMode.PARTIAL_ADVANCE);
        assertThat(forcedFull.enforcedPaymentMode()).isEqualTo(PaymentMode.FULL);
        assertThat(forcedFull.advancePercentageApplied()).isEqualByComparingTo("100.00");
        assertThat(forcedFull.pickupBalanceDue()).isEqualByComparingTo("0.00");
    }

    @Test
    void cancellationSnapshot_preservesImmutableTiersV5FallbackDepositProtectionNoShowAndDisruptions() {
        TestEntities fx = createBaseEntities();
        Instant now = Instant.now();
        Instant pickup = now.plus(72, ChronoUnit.HOURS);

        // Configure partner policy + tier (>48h = 0%, 12-48h = 15%, and leave <12h to V5 10% seed fallback or policy test)
        PartnerPolicy pp = new PartnerPolicy();
        pp.setBusiness(fx.business());
        pp.setCancellationDeadlineHours(24);
        pp.setFreeCancellationAllowed(true);
        pp.setCancellationFeePercentage(new BigDecimal("10.00"));
        pp.setGatewayFeeTreatment(GatewayFeeTreatment.DEDUCTED_FROM_REFUND);
        pp.setNoShowGraceMinutes(120);
        pp.setNoShowPenaltyPercentage(new BigDecimal("100.00"));
        partnerPolicyRepository.save(pp);

        PartnerCancellationTier tierEarly = new PartnerCancellationTier();
        tierEarly.setBusiness(fx.business());
        tierEarly.setMinHoursBeforePickup(48);
        tierEarly.setFeePercentage(BigDecimal.ZERO);
        tierEarly.setPriority(10);
        cancellationTierRepository.save(tierEarly);

        PartnerCancellationTier tierMid = new PartnerCancellationTier();
        tierMid.setBusiness(fx.business());
        tierMid.setMinHoursBeforePickup(12);
        tierMid.setMaxHoursBeforePickup(48);
        tierMid.setFeePercentage(new BigDecimal("15.00"));
        tierMid.setPriority(20);
        cancellationTierRepository.save(tierMid);

        CancellationPolicySnapshot frozen = cancellationSnapshotService.captureSnapshot(
                fx.business().getId(), new BigDecimal("2000.00"), now
        );
        String frozenJson = cancellationSnapshotService.serialize(frozen);

        // Mutate live DB policy AFTER capture — must NOT affect already-captured booking snapshot
        pp.setCancellationFeePercentage(new BigDecimal("50.00"));
        pp.setNoShowGraceMinutes(15);
        partnerPolicyRepository.saveAndFlush(pp);

        CancellationPolicySnapshot restored = cancellationSnapshotService.deserialize(frozenJson);
        assertThat(restored.defaultFeePercentage()).isEqualByComparingTo("10.00");
        assertThat(restored.noShowGraceMinutes()).isEqualTo(120);

        // Cancel 24h before pickup -> hits tierMid (15% of 2000 = 300), deducts gateway fee 20, protects 100% of 1000 online deposit
        CancellationSettlementBreakdown midCancel = cancellationSnapshotService.evaluateCancellation(
                restored, pickup, pickup.minus(24, ChronoUnit.HOURS),
                new BigDecimal("2000.00"), new BigDecimal("1000.00"),
                new BigDecimal("1000.00"), new BigDecimal("20.00")
        );
        assertThat(midCancel.rentalPenaltyAmount()).isEqualByComparingTo("300.00");
        assertThat(midCancel.gatewayFeeDeducted()).isEqualByComparingTo("20.00");
        assertThat(midCancel.netRentalRefundAmount()).isEqualByComparingTo("680.00");
        assertThat(midCancel.securityDepositRefundAmount()).isEqualByComparingTo("1000.00");
        assertThat(midCancel.depositProtected()).isTrue();
        assertThat(midCancel.totalRefundAmount()).isEqualByComparingTo("1680.00");

        // Cancel 4h before pickup (falls outside configured tiers -> V5 10% seed fallback: 10% of 2000 = 200)
        CancellationSettlementBreakdown lateFallback = cancellationSnapshotService.evaluateCancellation(
                restored, pickup, pickup.minus(4, ChronoUnit.HOURS),
                new BigDecimal("2000.00"), new BigDecimal("500.00"),
                new BigDecimal("1000.00"), BigDecimal.ZERO
        );
        assertThat(lateFallback.rentalPenaltyAmount()).isEqualByComparingTo("200.00");
        assertThat(lateFallback.netRentalRefundAmount()).isEqualByComparingTo("300.00");
        assertThat(lateFallback.securityDepositRefundAmount()).isEqualByComparingTo("1000.00");

        // Ferry cancellation disruption -> 100% refund + rescheduling allowed
        CancellationSettlementBreakdown ferryOverride = cancellationSnapshotService.evaluateDisruptionOverride(
                restored, DisruptionExceptionType.FERRY_CANCELLATION,
                new BigDecimal("500.00"), new BigDecimal("1000.00"), true
        );
        assertThat(ferryOverride.rentalPenaltyAmount()).isEqualByComparingTo("0.00");
        assertThat(ferryOverride.netRentalRefundAmount()).isEqualByComparingTo("500.00");
        assertThat(ferryOverride.securityDepositRefundAmount()).isEqualByComparingTo("1000.00");
        assertThat(ferryOverride.reschedulingEligible()).isTrue();

        // NO_SHOW before 120m grace or without evidence rejected; after 120m with evidence succeeds & protects deposit
        assertThatThrownBy(() -> cancellationSnapshotService.evaluateNoShow(
                restored, pickup, pickup.plus(60, ChronoUnit.MINUTES),
                "Guest did not arrive", null, new BigDecimal("500.00"), new BigDecimal("1000.00")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("grace window");

        assertThatThrownBy(() -> cancellationSnapshotService.evaluateNoShow(
                restored, pickup, pickup.plus(130, ChronoUnit.MINUTES),
                "   ", null, new BigDecimal("500.00"), new BigDecimal("1000.00")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("evidence");

        CancellationSettlementBreakdown noShow = cancellationSnapshotService.evaluateNoShow(
                restored, pickup, pickup.plus(130, ChronoUnit.MINUTES),
                "Desk log signed by supervisor at 11:10 IST", "handover_evidence_01",
                new BigDecimal("500.00"), new BigDecimal("1000.00")
        );
        assertThat(noShow.rentalPenaltyAmount()).isEqualByComparingTo("500.00");
        assertThat(noShow.netRentalRefundAmount()).isEqualByComparingTo("0.00");
        assertThat(noShow.securityDepositRefundAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void commissionCalculation_excludesGstAndDepositAndCreatesReceivableOnExcessOfflineCollection() {
        TestEntities fx = createBaseEntities();

        Booking booking = new Booking();
        booking.setBookingReference("IR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        booking.setUser(fx.tourist());
        booking.setVehicle(fx.vehicle());
        booking.setBusiness(fx.business());
        booking.setPickupLocation(fx.location());
        booking.setReturnLocation(fx.location());
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        booking.setStartTime(start);
        booking.setEndTime(start.plus(2, ChronoUnit.DAYS));
        booking.setTotalDays(2);
        booking.setDailyRate(new BigDecimal("1000.00"));
        booking.setBaseRentalAmount(new BigDecimal("2000.00"));
        booking.setTaxAmount(new BigDecimal("100.00")); // 5% GST on 2000
        booking.setCommissionRateSnapshot(new BigDecimal("4.00"));
        booking.setSecurityDepositAmount(new BigDecimal("3000.00")); // Excluded from commission
        booking.setTotalPayableAmount(new BigDecimal("2100.00")); // 2000 + 100 GST (deposit separate)
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setPaymentMode(PaymentMode.PARTIAL_ADVANCE);
        booking.setBookingMode(BookingMode.INSTANT);
        booking = bookingRepository.saveAndFlush(booking);

        // Case 1: Only ₹50 online collected (less than ₹80 commission = 4% of ₹2000) and ₹2050 offline collected
        Commission commission = commissionCalculationService.calculateAndRecord(
                booking,
                new BigDecimal("50.00"),
                new BigDecimal("2050.00")
        );
        assertThat(commission.getCommissionAmount()).isEqualByComparingTo("80.00");
        assertThat(commission.getTaxExcludedAmount()).isEqualByComparingTo("100.00");
        assertThat(commission.getDepositExcludedAmount()).isEqualByComparingTo("3000.00");
        assertThat(commission.getPartnerPayoutAmount()).isEqualByComparingTo("0.00");
        assertThat(commission.getPartnerReceivableAmount()).isEqualByComparingTo("30.00");

        PartnerReceivable receivable = receivableRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(receivable.getAmountDueFromPartner()).isEqualByComparingTo("30.00");
        assertThat(receivable.getStatus()).isEqualTo(ReceivableStatus.OPEN);
    }

    private TestEntities createBaseEntities() {
        User owner = createUser("ROLE_PARTNER_OWNER", "s6aOwn");
        User tourist = createUser("ROLE_TOURIST", "s6aTr");

        Island island = islandRepository.findByCode("PORT_BLAIR")
                .orElseGet(() -> islandRepository.findAll().getFirst());
        Location loc = locationRepository.findByIslandIdAndActiveTrue(island.getId()).getFirst();
        VehicleCategory cat = categoryRepository.findAll().getFirst();

        Business biz = new Business();
        biz.setOwnerUser(owner);
        biz.setTradeName("S6A Rentals " + UUID.randomUUID().toString().substring(0, 6));
        biz.setLegalName("S6A Rentals Pvt Ltd");
        biz.setGstin("35ABCDE1234F1Z5");
        biz.setPanNumber("ABCDE1234F");
        biz.setVerificationStatus(VerificationStatus.APPROVED);
        biz = businessRepository.save(biz);

        Vehicle v = new Vehicle();
        v.setBusiness(biz);
        v.setIsland(island);
        v.setPickupLocation(loc);
        v.setReturnLocation(loc);
        v.setCategory(cat);
        v.setTitle("Stage 6A Test Scooter");
        v.setMake("Honda");
        v.setModel("Activa 6G");
        v.setModelYear(2025);
        v.setRegistrationNumber("AN01S" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        v.setDailyRate(new BigDecimal("1000.00"));
        v.setSecurityDeposit(new BigDecimal("2000.00"));
        v.setStatus(VehicleStatus.ACTIVE);
        v = vehicleRepository.save(v);

        return new TestEntities(owner, tourist, biz, island, loc, cat, v);
    }

    private User createUser(String roleName, String prefix) {
        String tag = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        User user = new User();
        user.setEmail(prefix + "_" + tag + "@islandride.test");
        user.setPhone("+9194" + tag.replaceAll("[^0-9]", "7").substring(0, 8));
        user.setPasswordHash(passwordEncoder.encode("StrongPass123!"));
        user.setFirstName(prefix);
        user.setLastName("Test");
        user.setStatus(UserStatus.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    private record TestEntities(
            User owner,
            User tourist,
            Business business,
            Island island,
            Location location,
            VehicleCategory category,
            Vehicle vehicle
    ) {
    }
}

