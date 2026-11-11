package com.islandride.booking;

import com.islandride.administration.repository.AuditLogRepository;
import com.islandride.availability.domain.HoldPurpose;
import com.islandride.availability.domain.HoldStatus;
import com.islandride.availability.domain.InventoryHold;
import com.islandride.availability.repository.InventoryHoldRepository;
import com.islandride.availability.service.AvailabilityService;
import com.islandride.availability.service.InventoryHoldService;
import com.islandride.booking.domain.Booking;
import com.islandride.booking.domain.BookingStatus;
import com.islandride.booking.domain.BookingStatusHistory;
import com.islandride.booking.domain.PaymentMode;
import com.islandride.booking.repository.BookingRepository;
import com.islandride.booking.repository.BookingStatusHistoryRepository;
import com.islandride.booking.service.BookingActorRole;
import com.islandride.booking.service.BookingLifecycleFoundationService;
import com.islandride.core.dto.PageResponse;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.ConflictException;
import com.islandride.core.exception.IdempotencyConflictException;
import com.islandride.core.exception.InvalidStateTransitionException;
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
import com.islandride.inventory.dto.PublicVehicleSummaryDto;
import com.islandride.inventory.repository.VehicleCategoryRepository;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.inventory.service.PublicCatalogService;
import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.BusinessLocation;
import com.islandride.partner.domain.BusinessLocationId;
import com.islandride.partner.domain.PartnerStaff;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.partner.repository.BusinessLocationRepository;
import com.islandride.partner.repository.BusinessRepository;
import com.islandride.partner.repository.PartnerStaffRepository;
import com.islandride.pricing.domain.PartnerOperatingHours;
import com.islandride.pricing.repository.PartnerOperatingHoursRepository;
import com.islandride.pricing.service.OperatingHoursService;
import jakarta.persistence.EntityManager;
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

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
class Stage6AHoldConcurrencyTurnaroundAndPermissionsIntegrationTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private InventoryHoldService holdService;
    @Autowired
    private AvailabilityService availabilityService;
    @Autowired
    private PublicCatalogService publicCatalogService;
    @Autowired
    private OperatingHoursService operatingHoursService;
    @Autowired
    private BookingLifecycleFoundationService bookingLifecycleService;
    @Autowired
    private TenantSecurityService tenantSecurityService;
    @Autowired
    private InventoryHoldRepository holdRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private BookingStatusHistoryRepository statusHistoryRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private BusinessLocationRepository businessLocationRepository;
    @Autowired
    private PartnerStaffRepository staffRepository;
    @Autowired
    private PartnerOperatingHoursRepository operatingHoursRepository;
    @Autowired
    private IslandRepository islandRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private VehicleCategoryRepository categoryRepository;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManager entityManager;

    @Test
    void operatingHoursAndIslandIsolation_enforceDayScheduleHubOverridesAndSameIslandRules() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Fixture fx = tx.execute(s -> createCommittedFixture());
        assertThat(fx).isNotNull();
        try {
            LocalDate nextSunday = nextDayOfWeek(DayOfWeek.SUNDAY);
            LocalDate nextMonday = nextSunday.plusDays(1);

            tx.executeWithoutResult(s -> {
                // Default open Mon-Sun 08:00-18:00, except Sunday closed on hub override LOC_<id>
                PartnerOperatingHours sundayClosed = new PartnerOperatingHours();
                sundayClosed.setBusiness(businessRepository.getReferenceById(fx.businessId()));
                sundayClosed.setLocation(locationRepository.getReferenceById(fx.pickupLocationId()));
                sundayClosed.setScopeKey("LOC_" + fx.pickupLocationId());
                sundayClosed.setDayOfWeek(DayOfWeek.SUNDAY.getValue());
                sundayClosed.setOpen(false);
                operatingHoursRepository.save(sundayClosed);
            });

            Vehicle vehicle = tx.execute(s -> {
                Vehicle v = vehicleRepository.findById(fx.vehicleId()).orElseThrow();
                v.getIsland().getId();
                v.getBusiness().getId();
                v.getPickupLocation().getId();
                v.getReturnLocation().getId();
                return v;
            });

            Instant sundayMorning = ZonedDateTime.of(nextSunday, LocalTime.of(10, 0), IST).toInstant();
            Instant mondayAfternoon = ZonedDateTime.of(nextMonday, LocalTime.of(14, 0), IST).toInstant();

            // Sunday pickup rejected due to hub override closed=false
            assertThatThrownBy(() -> operatingHoursService.validateLocationsAndOperatingHours(
                    vehicle, null, null, sundayMorning, mondayAfternoon))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("closed");

            // Outside default evening close (22:00 IST > 21:00 IST default) rejected
            Instant mondayLateNight = ZonedDateTime.of(nextMonday, LocalTime.of(22, 0), IST).toInstant();
            assertThatThrownBy(() -> operatingHoursService.validateLocationsAndOperatingHours(
                    vehicle, null, null, mondayAfternoon, mondayLateNight))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("operating hours");

            // Cross-island dropoff rejected
            Location otherIslandHub = tx.execute(s -> {
                List<Island> islands = islandRepository.findAll();
                return islands.stream()
                        .filter(isl -> !isl.getId().equals(fx.islandId()))
                        .flatMap(isl -> locationRepository.findByIslandIdAndActiveTrue(isl.getId()).stream())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Expected at least two islands with active hubs in seeded DB."));
            });
            Instant mondayMorning = ZonedDateTime.of(nextMonday, LocalTime.of(9, 0), IST).toInstant();
            assertThatThrownBy(() -> operatingHoursService.validateLocationsAndOperatingHours(
                    vehicle, null, otherIslandHub, mondayMorning, mondayAfternoon))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Cross-island");

            // Valid Monday 09:00 -> 14:00 succeeds
            assertThat(operatingHoursService.validateLocationsAndOperatingHours(
                    vehicle, null, null, mondayMorning, mondayAfternoon).resolvedPickupLocation().getId())
                    .isEqualTo(fx.pickupLocationId());
        } finally {
            cleanupFixture(fx, List.of());
        }
    }

    @Test
    void turnaroundBuffer_60mDefaultEnforcedConsistentlyAcrossHoldAvailabilityAndCatalogSearch() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Fixture fx = tx.execute(s -> createCommittedFixture());
        assertThat(fx).isNotNull();
        try {
            LocalDate targetDay = LocalDate.now(IST).plusDays(6);
            Instant bookStart = ZonedDateTime.of(targetDay, LocalTime.of(8, 0), IST).toInstant();
            Instant bookEnd = ZonedDateTime.of(targetDay, LocalTime.of(12, 0), IST).toInstant();

            tx.executeWithoutResult(s -> {
                Booking b = new Booking();
                b.setBookingReference("IR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
                b.setUser(userRepository.getReferenceById(fx.touristId()));
                b.setVehicle(vehicleRepository.getReferenceById(fx.vehicleId()));
                b.setBusiness(businessRepository.getReferenceById(fx.businessId()));
                b.setPickupLocation(locationRepository.getReferenceById(fx.pickupLocationId()));
                b.setReturnLocation(locationRepository.getReferenceById(fx.pickupLocationId()));
                b.setStartTime(bookStart);
                b.setEndTime(bookEnd);
                b.setTotalDays(1);
                b.setDailyRate(new BigDecimal("1000.00"));
                b.setBaseRentalAmount(new BigDecimal("1000.00"));
                b.setTotalPayableAmount(new BigDecimal("1050.00"));
                b.setTurnaroundBufferMinutes(60);
                b.setStatus(BookingStatus.CONFIRMED);
                b.setPaymentMode(PaymentMode.FULL);
                b.setBookingMode(BookingMode.INSTANT);
                bookingRepository.save(b);
            });

            // 59m gap after bookEnd -> blocked across AvailabilityService, Catalog Search, and InventoryHoldService
            Instant gap59Start = bookEnd.plus(59, ChronoUnit.MINUTES); // 12:59 IST
            Instant gap59End = bookEnd.plus(180, ChronoUnit.MINUTES);  // 15:00 IST

            assertThat(availabilityService.isVehicleAvailable(fx.vehicleId(), gap59Start, gap59End)).isFalse();
            PageResponse<PublicVehicleSummaryDto> searchBlocked = publicCatalogService.searchVehicles(
                    fx.islandId(), null, null, null, null, null, null, fx.vehicleUniqueToken(),
                    null, null, null, null, null, null, gap59Start, gap59End, null, 0, 10);
            assertThat(searchBlocked.content()).isEmpty();

            assertThatThrownBy(() -> holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                    fx.touristId(), fx.vehiclePublicId(), gap59Start, gap59End,
                    HoldPurpose.CHECKOUT, null, null, "hold-59-" + UUID.randomUUID())))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("turnaround buffer");

            // Exact 60m gap after bookEnd -> available across all 3 paths!
            Instant gap60Start = bookEnd.plus(60, ChronoUnit.MINUTES); // 13:00 IST
            Instant gap60End = bookEnd.plus(240, ChronoUnit.MINUTES);  // 16:00 IST

            assertThat(availabilityService.isVehicleAvailable(fx.vehicleId(), gap60Start, gap60End)).isTrue();
            PageResponse<PublicVehicleSummaryDto> searchFree = publicCatalogService.searchVehicles(
                    fx.islandId(), null, null, null, null, null, null, fx.vehicleUniqueToken(),
                    null, null, null, null, null, null, gap60Start, gap60End, null, 0, 10);
            assertThat(searchFree.content()).hasSize(1);
            assertThat(searchFree.content().getFirst().publicId()).isEqualTo(fx.vehiclePublicId());

            InventoryHoldService.HoldResult hold = holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                    fx.touristId(), fx.vehiclePublicId(), gap60Start, gap60End,
                    HoldPurpose.CHECKOUT, null, null, "hold-60-" + UUID.randomUUID()));
            assertThat(hold.status()).isEqualTo(HoldStatus.HELD);
            assertThat(hold.turnaroundBufferMinutes()).isEqualTo(60);
        } finally {
            cleanupFixture(fx, List.of());
        }
    }

    @Test
    void concurrentHoldAcquisition_20ThreadsOnSameVehicleAllowsExactlyOneWinner() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Fixture fx = tx.execute(s -> createCommittedFixture());
        assertThat(fx).isNotNull();

        List<Long> touristIds = tx.execute(s -> {
            List<Long> ids = new ArrayList<>(20);
            for (int i = 0; i < 20; i++) {
                ids.add(createUser("ROLE_TOURIST", "race" + i).getId());
            }
            return ids;
        });

        try {
            LocalDate targetDay = LocalDate.now(IST).plusDays(7);
            Instant start = ZonedDateTime.of(targetDay, LocalTime.of(9, 0), IST).toInstant();
            Instant end = ZonedDateTime.of(targetDay, LocalTime.of(17, 0), IST).toInstant();

            int threadCount = 20;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger successes = new AtomicInteger();
            AtomicInteger conflicts = new AtomicInteger();
            List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                final Long userId = touristIds.get(i);
                final String idemKey = "race-idem-" + i + "-" + UUID.randomUUID();
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await(10, TimeUnit.SECONDS);
                        holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                                userId, fx.vehiclePublicId(), start, end,
                                HoldPurpose.CHECKOUT, null, null, idemKey
                        ));
                        successes.incrementAndGet();
                    } catch (ConflictException ex) {
                        conflicts.incrementAndGet();
                    } catch (Throwable t) {
                        unexpectedErrors.add(t);
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<?> f : futures) {
                f.get(20, TimeUnit.SECONDS);
            }
            pool.shutdownNow();

            assertThat(unexpectedErrors).isEmpty();
            assertThat(successes.get()).isEqualTo(1);
            assertThat(conflicts.get()).isEqualTo(threadCount - 1);
            assertThat(holdRepository.findByVehicleId(fx.vehicleId()).stream()
                    .filter(h -> h.getStatus() == HoldStatus.HELD)
                    .count()).isEqualTo(1L);
        } finally {
            cleanupFixture(fx, touristIds);
        }
    }

    @Test
    void idempotencyAndHoldLifecycle_handlesReplayHashConflictConversionAndStaleExpiry() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Fixture fx = tx.execute(s -> createCommittedFixture());
        assertThat(fx).isNotNull();
        try {
            LocalDate targetDay = LocalDate.now(IST).plusDays(8);
            Instant start = ZonedDateTime.of(targetDay, LocalTime.of(9, 0), IST).toInstant();
            Instant end = ZonedDateTime.of(targetDay, LocalTime.of(15, 0), IST).toInstant();
            String key = "idem-hold-" + UUID.randomUUID();

            InventoryHoldService.AcquireHoldCommand cmd = new InventoryHoldService.AcquireHoldCommand(
                    fx.touristId(), fx.vehiclePublicId(), start, end, HoldPurpose.CHECKOUT, null, null, key
            );
            InventoryHoldService.HoldResult first = holdService.acquireHold(cmd);
            InventoryHoldService.HoldResult replay = holdService.acquireHold(cmd);

            assertThat(replay.publicId()).isEqualTo(first.publicId());
            assertThat(holdRepository.findByVehicleId(fx.vehicleId())).hasSize(1);

            // Same key with changed endTime triggers IdempotencyConflictException (409)
            InventoryHoldService.AcquireHoldCommand tampered = new InventoryHoldService.AcquireHoldCommand(
                    fx.touristId(), fx.vehiclePublicId(), start, end.plus(1, ChronoUnit.HOURS),
                    HoldPurpose.CHECKOUT, null, null, key
            );
            assertThatThrownBy(() -> holdService.acquireHold(tampered))
                    .isInstanceOf(IdempotencyConflictException.class);

            // Atomic conversion succeeds once and rejects second claim
            holdService.convertHoldToBookingOrFail(first.id(), fx.touristId(), Instant.now());
            assertThat(holdRepository.findById(first.id()).orElseThrow().getStatus()).isEqualTo(HoldStatus.CONVERTED);
            assertThatThrownBy(() -> holdService.convertHoldToBookingOrFail(first.id(), fx.touristId(), Instant.now()))
                    .isInstanceOf(ConflictException.class);

            // Stale hold expiry cleanup moves HELD with expiresAt <= now -> EXPIRED
            InventoryHoldService.HoldResult second = holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                    fx.touristId(), fx.vehiclePublicId(), start, end, HoldPurpose.CHECKOUT,
                    null, null, "idem-hold-2-" + UUID.randomUUID()
            ));
            tx.executeWithoutResult(s -> {
                InventoryHold h = holdRepository.findById(second.id()).orElseThrow();
                h.setExpiresAt(Instant.now().minusSeconds(30));
                holdRepository.save(h);
            });
            int expiredCount = holdService.expireStaleHolds();
            assertThat(expiredCount).isGreaterThanOrEqualTo(1);
            assertThat(holdRepository.findById(second.id()).orElseThrow().getStatus()).isEqualTo(HoldStatus.EXPIRED);
        } finally {
            cleanupFixture(fx, List.of());
        }
    }

    @Test
    void bookingLifecycleFoundation_persistsHistoryAndAuditTrailAndBlocksInvalidTransitions() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Fixture fx = tx.execute(s -> createCommittedFixture());
        assertThat(fx).isNotNull();
        try {
            Booking created = tx.execute(s -> {
                Booking b = new Booking();
                b.setBookingReference("IR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
                b.setUser(userRepository.getReferenceById(fx.touristId()));
                b.setVehicle(vehicleRepository.getReferenceById(fx.vehicleId()));
                b.setBusiness(businessRepository.getReferenceById(fx.businessId()));
                b.setPickupLocation(locationRepository.getReferenceById(fx.pickupLocationId()));
                b.setReturnLocation(locationRepository.getReferenceById(fx.pickupLocationId()));
                Instant st = Instant.now().plus(3, ChronoUnit.DAYS);
                b.setStartTime(st);
                b.setEndTime(st.plus(1, ChronoUnit.DAYS));
                b.setTotalDays(1);
                b.setDailyRate(new BigDecimal("1000.00"));
                b.setBaseRentalAmount(new BigDecimal("1000.00"));
                b.setTotalPayableAmount(new BigDecimal("1050.00"));
                b.setPickupBalanceDue(new BigDecimal("787.50"));
                b.setStatus(BookingStatus.AWAITING_PARTNER_APPROVAL);
                b.setApprovalExpiresAt(Instant.now().plus(2, ChronoUnit.HOURS));
                b.setPaymentMode(PaymentMode.PARTIAL_ADVANCE);
                b.setBookingMode(BookingMode.APPROVAL);
                return bookingRepository.save(b);
            });

            Instant now = Instant.now();
            Booking approved = bookingLifecycleService.transitionStatus(
                    created, BookingStatus.APPROVED, BookingActorRole.PARTNER, fx.ownerId(), "Partner verified fleet", now
            );
            assertThat(approved.getStatus()).isEqualTo(BookingStatus.APPROVED);
            assertThat(approved.getApprovedBy()).isEqualTo(fx.ownerId());

            // Invalid skip APPROVED -> ACTIVE rejected
            assertThatThrownBy(() -> bookingLifecycleService.transitionStatus(
                    approved, BookingStatus.ACTIVE, BookingActorRole.PARTNER, fx.ownerId(), "Skip payment", now.plusSeconds(10)))
                    .isInstanceOf(InvalidStateTransitionException.class);

            Booking confirmed = bookingLifecycleService.transitionStatus(
                    approved, BookingStatus.CONFIRMED, BookingActorRole.SYSTEM, fx.touristId(), "Advance verified", now.plusSeconds(20)
            );
            assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

            List<BookingStatusHistory> history = statusHistoryRepository.findByBookingIdOrderByCreatedAtAsc(confirmed.getId());
            assertThat(history).hasSize(2);
            assertThat(history.get(0).getFromStatus()).isEqualTo("AWAITING_PARTNER_APPROVAL");
            assertThat(history.get(0).getToStatus()).isEqualTo("APPROVED");
            assertThat(history.get(1).getToStatus()).isEqualTo("CONFIRMED");
            assertThat(auditLogRepository.findByEntityNameAndEntityIdOrderByCreatedAtDesc("Booking", confirmed.getPublicId()))
                    .hasSize(2);
        } finally {
            cleanupFixture(fx, List.of());
        }
    }

    @Test
    void partnerStaffPermissions_enforceExplicitOperationalFlagsAndFinanceSeparation() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Fixture fx = tx.execute(s -> createCommittedFixture());
        assertThat(fx).isNotNull();

        User plainStaff = tx.execute(s -> {
            User u = createUser("ROLE_PARTNER_STAFF", "stfPlain");
            PartnerStaff ps = new PartnerStaff();
            ps.setBusiness(businessRepository.getReferenceById(fx.businessId()));
            ps.setUser(u);
            ps.setRoleInBusiness("STAFF");
            ps.setActive(true);
            staffRepository.save(ps);
            return u;
        });

        List<Long> extraUserIds = new ArrayList<>();
        extraUserIds.add(plainStaff.getId());

        try {
            // Unapproved flags: all operational actions denied
            setPrincipal(plainStaff, fx.businessId());
            assertThat(tenantSecurityService.canApproveOrRejectBookings(fx.businessId())).isFalse();
            assertThat(tenantSecurityService.canRecordHandover(fx.businessId())).isFalse();
            assertThat(tenantSecurityService.canCollectOfflinePayments(fx.businessId())).isFalse();
            assertThat(tenantSecurityService.canMarkNoShow(fx.businessId())).isFalse();
            assertThatThrownBy(() -> tenantSecurityService.requireHandoverAccess(fx.businessId()))
                    .isInstanceOf(AccessDeniedException.class);

            // Grant handover + offline collection ONLY (no NO_SHOW, no booking approval)
            tx.executeWithoutResult(s -> {
                PartnerStaff ps = staffRepository.findByBusinessIdAndUserId(fx.businessId(), plainStaff.getId()).orElseThrow();
                ps.setCanRecordHandover(true);
                ps.setCanCollectOfflinePayments(true);
                staffRepository.save(ps);
            });
            assertThat(tenantSecurityService.canRecordHandover(fx.businessId())).isTrue();
            assertThat(tenantSecurityService.canCollectOfflinePayments(fx.businessId())).isTrue();
            assertThat(tenantSecurityService.canMarkNoShow(fx.businessId())).isFalse();
            assertThat(tenantSecurityService.canApproveOrRejectBookings(fx.businessId())).isFalse();

            // Support agent can escalate refund, only finance/super admin can approve financial refunds
            User support = tx.execute(s -> createUser("ROLE_SUPPORT_AGENT", "supRefund"));
            extraUserIds.add(support.getId());
            User finance = tx.execute(s -> createUser("ROLE_FINANCE_ADMIN", "finRefund"));
            extraUserIds.add(finance.getId());

            setPrincipal(support, null);
            assertThat(tenantSecurityService.canEscalateRefund()).isTrue();
            assertThat(tenantSecurityService.canApproveFinancialRefunds()).isFalse();
            assertThatThrownBy(() -> tenantSecurityService.requireFinancialRefundApprovalAccess())
                    .isInstanceOf(AccessDeniedException.class);

            setPrincipal(finance, null);
            assertThat(tenantSecurityService.canApproveFinancialRefunds()).isTrue();
            tenantSecurityService.requireFinancialRefundApprovalAccess();
        } finally {
            SecurityContextHolder.clearContext();
            cleanupFixture(fx, extraUserIds);
        }
    }

    private void cleanupFixture(Fixture fx, List<Long> extraUserIds) {
        if (fx == null) {
            return;
        }
        List<Long> allUserIds = new ArrayList<>();
        allUserIds.add(fx.ownerId());
        allUserIds.add(fx.touristId());
        if (extraUserIds != null) {
            allUserIds.addAll(extraUserIds);
        }
        List<String> actorKeys = allUserIds.stream().map(id -> "user:" + id).toList();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<String> bookingPublicIds = entityManager.createQuery(
                            "SELECT b.publicId FROM Booking b WHERE b.vehicle.id = :vid", String.class)
                    .setParameter("vid", fx.vehicleId())
                    .getResultList();
            if (!bookingPublicIds.isEmpty()) {
                entityManager.createQuery(
                                "DELETE FROM AuditLog a WHERE a.entityName = 'Booking' AND a.entityId IN :ids")
                        .setParameter("ids", bookingPublicIds)
                        .executeUpdate();
            }
            entityManager.createQuery("DELETE FROM AuditLog a WHERE a.actorUser.id IN :uids")
                    .setParameter("uids", allUserIds)
                    .executeUpdate();
            entityManager.createQuery(
                            "DELETE FROM BookingStatusHistory h WHERE h.booking.id IN (SELECT b.id FROM Booking b WHERE b.vehicle.id = :vid)")
                    .setParameter("vid", fx.vehicleId())
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM Booking b WHERE b.vehicle.id = :vid")
                    .setParameter("vid", fx.vehicleId())
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM InventoryHold h WHERE h.vehicle.id = :vid")
                    .setParameter("vid", fx.vehicleId())
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM IdempotencyRecord r WHERE r.actorKey IN :keys")
                    .setParameter("keys", actorKeys)
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM PartnerOperatingHours o WHERE o.business.id = :bid")
                    .setParameter("bid", fx.businessId())
                    .executeUpdate();
            vehicleRepository.deleteById(fx.vehicleId());
            entityManager.createQuery("DELETE FROM BusinessLocation bl WHERE bl.business.id = :bid")
                    .setParameter("bid", fx.businessId())
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM PartnerStaff ps WHERE ps.business.id = :bid")
                    .setParameter("bid", fx.businessId())
                    .executeUpdate();
            businessRepository.deleteById(fx.businessId());
            entityManager.flush();
            userRepository.findAllById(allUserIds).forEach(userRepository::delete);
        });
    }

    private void setPrincipal(User user, Long businessId) {
        UserPrincipal principal = new TransactionTemplate(transactionManager).execute(s -> {
            User reloaded = userRepository.findById(user.getId()).orElseThrow();
            return UserPrincipal.create(reloaded, businessId);
        });
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private Fixture createCommittedFixture() {
        User owner = createUser("ROLE_PARTNER_OWNER", "s6hOwn");
        User tourist = createUser("ROLE_TOURIST", "s6hTr");

        Island island = islandRepository.findByCode("PORT_BLAIR")
                .orElseGet(() -> islandRepository.findAll().getFirst());
        Location loc = locationRepository.findByIslandIdAndActiveTrue(island.getId()).getFirst();
        VehicleCategory cat = categoryRepository.findAll().getFirst();

        Business biz = new Business();
        biz.setOwnerUser(owner);
        biz.setTradeName("S6A Fleet " + UUID.randomUUID().toString().substring(0, 6));
        biz.setLegalName("S6A Fleet Pvt Ltd");
        biz.setGstin("35ABCDE1234F1Z5");
        biz.setPanNumber("ABCDE1234F");
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

        BusinessLocation hubLink = new BusinessLocation();
        hubLink.setId(new BusinessLocationId(biz.getId(), loc.getId()));
        hubLink.setBusiness(biz);
        hubLink.setLocation(loc);
        businessLocationRepository.save(hubLink);

        String token = "TKN" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        Vehicle v = new Vehicle();
        v.setBusiness(biz);
        v.setIsland(island);
        v.setPickupLocation(loc);
        v.setReturnLocation(loc);
        v.setCategory(cat);
        v.setTitle("Stage 6A Hold Scooter " + token);
        v.setMake("Honda");
        v.setModel("Activa " + token);
        v.setModelYear(2025);
        v.setRegistrationNumber("AN01H" + token.substring(0, 6));
        v.setDailyRate(new BigDecimal("1000.00"));
        v.setSecurityDeposit(new BigDecimal("2000.00"));
        v.setStatus(VehicleStatus.ACTIVE);
        v = vehicleRepository.save(v);

        return new Fixture(owner.getId(), tourist.getId(), biz.getId(), island.getId(), loc.getId(), v.getId(), v.getPublicId(), token);
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

    private static LocalDate nextDayOfWeek(DayOfWeek target) {
        LocalDate date = LocalDate.now(IST).plusDays(2);
        while (date.getDayOfWeek() != target) {
            date = date.plusDays(1);
        }
        return date;
    }

    private record Fixture(
            Long ownerId,
            Long touristId,
            Long businessId,
            Long islandId,
            Long pickupLocationId,
            Long vehicleId,
            String vehiclePublicId,
            String vehicleUniqueToken
    ) {
    }
}
