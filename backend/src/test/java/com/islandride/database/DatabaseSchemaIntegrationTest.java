package com.islandride.database;

import com.islandride.administration.domain.AuditLog;
import com.islandride.administration.domain.SystemSetting;
import com.islandride.administration.repository.AuditLogRepository;
import com.islandride.administration.repository.SystemSettingRepository;
import com.islandride.availability.domain.HoldStatus;
import com.islandride.availability.domain.InventoryHold;
import com.islandride.availability.repository.InventoryHoldRepository;
import com.islandride.booking.domain.*;
import com.islandride.booking.repository.BookingRepository;
import com.islandride.booking.repository.HandoverInspectionRepository;
import com.islandride.booking.repository.OverbookingExceptionRecordRepository;
import com.islandride.booking.repository.SecurityDepositRecordRepository;
import com.islandride.core.idempotency.IdempotencyRecord;
import com.islandride.core.idempotency.IdempotencyRecordRepository;
import com.islandride.core.idempotency.IdempotencyStatus;
import com.islandride.commission.domain.Commission;
import com.islandride.commission.domain.CommissionStatus;
import com.islandride.commission.domain.Settlement;
import com.islandride.commission.domain.SettlementStatus;
import com.islandride.commission.repository.CommissionRepository;
import com.islandride.commission.repository.SettlementRepository;
import com.islandride.geography.domain.Country;
import com.islandride.geography.domain.Island;
import com.islandride.geography.domain.Location;
import com.islandride.geography.domain.State;
import com.islandride.geography.repository.IslandRepository;
import com.islandride.geography.repository.LocationRepository;
import com.islandride.identityaccess.domain.*;
import com.islandride.identityaccess.repository.RoleRepository;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.inventory.domain.*;
import com.islandride.inventory.repository.VehicleCategoryRepository;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.notifications.domain.Notification;
import com.islandride.notifications.domain.NotificationChannel;
import com.islandride.notifications.domain.NotificationTemplate;
import com.islandride.notifications.repository.NotificationRepository;
import com.islandride.notifications.repository.NotificationTemplateRepository;
import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.PartnerStaff;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.partner.repository.BusinessRepository;
import com.islandride.partner.repository.PartnerStaffRepository;
import com.islandride.paymentfinance.domain.*;
import com.islandride.paymentfinance.repository.OfflineCollectionRepository;
import com.islandride.paymentfinance.repository.PartnerReceivableRepository;
import com.islandride.paymentfinance.repository.PaymentAttemptRepository;
import com.islandride.paymentfinance.repository.PaymentRepository;
import com.islandride.paymentfinance.repository.PaymentWebhookEventRepository;
import com.islandride.paymentfinance.repository.RefundRepository;
import com.islandride.reviews.domain.Review;
import com.islandride.reviews.domain.ReviewReport;
import com.islandride.reviews.domain.ReviewStatus;
import com.islandride.reviews.repository.ReviewReportRepository;
import com.islandride.reviews.repository.ReviewRepository;
import com.islandride.support.domain.*;
import com.islandride.support.repository.DamageReportRepository;
import com.islandride.support.repository.SupportTicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class DatabaseSchemaIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private IslandRepository islandRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private VehicleCategoryRepository categoryRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private InventoryHoldRepository holdRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private HandoverInspectionRepository inspectionRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private OfflineCollectionRepository offlineCollectionRepository;

    @Autowired
    private CommissionRepository commissionRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewReportRepository reviewReportRepository;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private DamageReportRepository damageReportRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationTemplateRepository notificationTemplateRepository;

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SecurityDepositRecordRepository securityDepositRecordRepository;

    @Autowired
    private OverbookingExceptionRecordRepository overbookingExceptionRecordRepository;

    @Autowired
    private PaymentWebhookEventRepository paymentWebhookEventRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private PartnerReceivableRepository partnerReceivableRepository;

    @Autowired
    private PartnerStaffRepository partnerStaffRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Test
    @DisplayName("Verify Flyway Seed Data: Roles, Islands, Categories, System Settings")
    void testBaselineSeedDataLoadedSuccessfully() {
        // 1. Verify 8 RBAC Roles seeded by V2
        List<Role> roles = roleRepository.findAll();
        assertThat(roles).hasSizeGreaterThanOrEqualTo(8);
        assertThat(roles.stream().map(Role::getName))
                .contains(
                        RoleType.ROLE_TOURIST.name(),
                        RoleType.ROLE_PARTNER_OWNER.name(),
                        RoleType.ROLE_SUPER_ADMIN.name(),
                        RoleType.ROLE_FINANCE_ADMIN.name(),
                        RoleType.ROLE_SUPPORT_AGENT.name()
                );

        // 2. Verify Andaman Islands seeded by V3
        List<Island> islands = islandRepository.findAll();
        assertThat(islands).isNotEmpty();
        assertThat(islands.stream().map(Island::getName))
                .contains("South Andaman (Port Blair)", "Swaraj Dweep (Havelock)", "Shaheed Dweep (Neil)");

        // 3. Verify Vehicle Categories seeded by V4
        List<VehicleCategory> categories = categoryRepository.findAll();
        assertThat(categories).hasSizeGreaterThanOrEqualTo(5);
        assertThat(categories.stream().map(VehicleCategory::getCode))
                .contains("SCOOTER", "MOTORCYCLE", "HATCHBACK", "SEDAN", "SUV");

        // 4. Verify System Settings seeded by V8
        Optional<SystemSetting> holdDurationSetting = systemSettingRepository.findBySettingKey("hold.duration.seconds");
        assertThat(holdDurationSetting).isPresent();
        assertThat(holdDurationSetting.get().getSettingValue()).isEqualTo("600");
    }

    @Test
    @DisplayName("Verify Complete Marketplace Domain Model Persistence & Relationships")
    void testEndToEndDomainPersistenceAndRelationships() {
        // 1. Create and persist User (Tourist)
        User tourist = new User();
        tourist.setEmail("tourist.test." + UUID.randomUUID() + "@islandride.in");
        tourist.setFirstName("Rahul");
        tourist.setLastName("Sharma");
        tourist.setPasswordHash("$2a$12$e80e...hashPlaceholder");
        tourist.setPhone("+919876543210");
        tourist.setStatus(UserStatus.ACTIVE);
        tourist = userRepository.saveAndFlush(tourist);
        assertThat(tourist.getId()).isNotNull();
        assertThat(tourist.getPublicId()).startsWith("usr_");

        // 2. Create Partner Owner User and Business
        User partnerUser = new User();
        partnerUser.setEmail("partner.owner." + UUID.randomUUID() + "@islandride.in");
        partnerUser.setFirstName("Vikram");
        partnerUser.setLastName("Singh");
        partnerUser.setPasswordHash("$2a$12$e80e...hashPlaceholder");
        partnerUser.setPhone("+919876543211");
        partnerUser = userRepository.saveAndFlush(partnerUser);

        Business business = new Business();
        business.setOwnerUser(partnerUser);
        business.setLegalName("Andaman Island Rentals LLP");
        business.setTradeName("Island Wheels Port Blair");
        business.setGstin("35AABCA1234F1Z5");
        business.setPanNumber("AABCA1234F");
        business.setVerificationStatus(VerificationStatus.APPROVED);
        business.setCommissionRate(new BigDecimal("4.00"));
        business = businessRepository.saveAndFlush(business);
        assertThat(business.getId()).isNotNull();
        assertThat(business.getPublicId()).startsWith("bus_");

        // 3. Retrieve Island & Category, create Vehicle
        Island havelock = islandRepository.findByCode("HAVELOCK")
                .orElseGet(() -> islandRepository.findAll().get(0));
        VehicleCategory scooterCategory = categoryRepository.findByCode("SCOOTER_110")
                .orElseGet(() -> categoryRepository.findAll().get(0));

        Vehicle vehicle = new Vehicle();
        vehicle.setBusiness(business);
        vehicle.setIsland(havelock);
        vehicle.setCategory(scooterCategory);
        vehicle.setMake("Honda");
        vehicle.setModel("Activa 6G");
        vehicle.setModelYear(2024);
        vehicle.setRegistrationNumber("AN-01-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        vehicle.setDailyRate(new BigDecimal("600.00"));
        vehicle.setSecurityDeposit(new BigDecimal("1000.00"));
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle.setBookingMode(BookingMode.INSTANT);
        vehicle = vehicleRepository.saveAndFlush(vehicle);
        assertThat(vehicle.getId()).isNotNull();

        // 4. Test Durable MySQL Inventory Hold
        Instant now = Instant.now();
        Instant holdStart = now.plus(1, ChronoUnit.DAYS);
        Instant holdEnd = holdStart.plus(3, ChronoUnit.DAYS);

        InventoryHold hold = new InventoryHold();
        hold.setVehicle(vehicle);
        hold.setUser(tourist);
        hold.setStartTime(holdStart);
        hold.setEndTime(holdEnd);
        hold.setHoldDurationSeconds(600);
        hold.setStatus(HoldStatus.HELD);
        hold.setExpiresAt(now.plusSeconds(600));
        hold = holdRepository.saveAndFlush(hold);
        assertThat(hold.getId()).isNotNull();
        assertThat(hold.getPublicId()).startsWith("hld_");

        // Overlapping hold availability check
        List<InventoryHold> overlappingHolds = holdRepository.findActiveOverlappingHolds(
                vehicle.getId(),
                holdStart.plus(1, ChronoUnit.HOURS),
                holdEnd.minus(1, ChronoUnit.HOURS),
                HoldStatus.HELD,
                now
        );
        assertThat(overlappingHolds).hasSize(1);

        // 5. Create Booking (Canonical single vehicle per booking contract)
        Location pickupLoc = locationRepository.findAll().get(0);
        Booking booking = new Booking();
        booking.setBookingReference("BK-" + System.currentTimeMillis());
        booking.setUser(tourist);
        booking.setVehicle(vehicle);
        booking.setBusiness(business);
        booking.setPickupLocation(pickupLoc);
        booking.setReturnLocation(pickupLoc);
        booking.setStartTime(holdStart);
        booking.setEndTime(holdEnd);
        booking.setTotalDays(3);
        booking.setDailyRate(new BigDecimal("600.00"));
        booking.setBaseRentalAmount(new BigDecimal("1800.00"));
        booking.setTotalPayableAmount(new BigDecimal("1800.00"));
        booking.setAdvancePaidAmount(new BigDecimal("450.00"));
        booking.setPickupBalanceDue(new BigDecimal("1350.00"));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaymentMode(PaymentMode.PARTIAL_ADVANCE);
        booking.setBookingMode(BookingMode.INSTANT);
        booking.addStatusHistory(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED, "Advance payment captured", tourist.getId());

        booking = bookingRepository.saveAndFlush(booking);
        assertThat(booking.getId()).isNotNull();
        assertThat(booking.getStatusHistory()).hasSize(1);

        // Overlapping bookings query
        List<Booking> overlappingBookings = bookingRepository.findOverlappingBookings(
                vehicle.getId(),
                holdStart.plus(2, ChronoUnit.HOURS),
                holdEnd.minus(2, ChronoUnit.HOURS),
                Set.of(BookingStatus.CANCELLED, BookingStatus.REJECTED, BookingStatus.PAYMENT_FAILED)
        );
        assertThat(overlappingBookings).hasSize(1);

        // 6. Handover Inspection & Photographic Evidence
        HandoverInspection pickupInspection = new HandoverInspection();
        pickupInspection.setBooking(booking);
        pickupInspection.setHandoverType(HandoverType.PICKUP);
        pickupInspection.setStaffUser(partnerUser);
        pickupInspection.setOdometerReading(12450);
        pickupInspection.setFuelPercentage(100);
        pickupInspection.setNotes("Clean condition, 2 ISI helmets provided");
        pickupInspection.addEvidence(EvidenceType.ODOMETER_PHOTO, "s3://islandride/odometer-12450.jpg", "image/jpeg", 11.6234, 92.7265);
        pickupInspection.addEvidence(EvidenceType.EXTERIOR_FRONT, "s3://islandride/front-inspect.jpg", "image/jpeg", 11.6234, 92.7265);
        pickupInspection = inspectionRepository.saveAndFlush(pickupInspection);
        assertThat(pickupInspection.getId()).isNotNull();
        assertThat(pickupInspection.getEvidenceList()).hasSize(2);

        // 7. Payment Ledger & Payment Attempt
        String idempotencyKey = "idempotency_pay_" + UUID.randomUUID();
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(new BigDecimal("450.00"));
        payment.setCurrency("INR");
        payment.setPaymentType(PaymentType.RENTAL_ADVANCE);
        payment.setPaymentMethod(PaymentMethod.GATEWAY);
        payment.setGatewayProvider("RAZORPAY");
        payment.setGatewayOrderId("order_rzp_" + UUID.randomUUID().toString().substring(0, 10));
        payment.setGatewayPaymentId("pay_rzp_" + UUID.randomUUID().toString().substring(0, 10));
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setIdempotencyKey(idempotencyKey);
        payment.addAttempt(1, "{\"order\":\"rzp\"}", "{\"status\":\"captured\"}", "SUCCESS", null, null);
        payment = paymentRepository.saveAndFlush(payment);
        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getAttempts()).hasSize(1);

        // 7.1 Refund Ledger (Auditable refund linked to payment & booking)
        Refund refund = new Refund();
        refund.setPayment(payment);
        refund.setBooking(booking);
        refund.setAmount(new BigDecimal("100.00"));
        refund.setCurrency("INR");
        refund.setReason("SECURITY_DEPOSIT_RETURN");
        refund.setStatus(RefundStatus.SUCCESS);
        refund.setIdempotencyKey("idempotency_ref_" + UUID.randomUUID());
        refund.setGatewayRefundId("rfnd_rzp_" + UUID.randomUUID().toString().substring(0, 10));
        refund.setReconciledAt(Instant.now());
        refund = refundRepository.saveAndFlush(refund);
        assertThat(refund.getId()).isNotNull();
        assertThat(refund.getPayment().getId()).isEqualTo(payment.getId());
        assertThat(refund.getBooking().getId()).isEqualTo(booking.getId());

        // 8. Offline Collection (Balance collected at physical hub)
        OfflineCollection offlineCollection = new OfflineCollection();
        offlineCollection.setBooking(booking);
        offlineCollection.setStaffUser(partnerUser);
        offlineCollection.setAmount(new BigDecimal("1350.00"));
        offlineCollection.setCollectionType("PICKUP_BALANCE");
        offlineCollection.setPaymentMethod("UPI_QR");
        offlineCollection.setReceiptReference("UPI-REC-98231");
        offlineCollection = offlineCollectionRepository.saveAndFlush(offlineCollection);
        assertThat(offlineCollection.getId()).isNotNull();

        // 9. Platform Commission
        Commission commission = new Commission();
        commission.setBooking(booking);
        commission.setBusiness(business);
        commission.setGrossBookingAmount(new BigDecimal("1800.00"));
        commission.setCommissionPercentage(new BigDecimal("4.00"));
        commission.setCommissionAmount(new BigDecimal("72.00"));
        commission.setPartnerPayoutAmount(new BigDecimal("1728.00"));
        commission.setStatus(CommissionStatus.ACCRUED);
        commission = commissionRepository.saveAndFlush(commission);
        assertThat(commission.getId()).isNotNull();

        // 10. Partner Settlement Batch
        Settlement settlement = new Settlement();
        settlement.setBusiness(business);
        settlement.setSettlementPeriodStart(LocalDate.now().minusDays(7));
        settlement.setSettlementPeriodEnd(LocalDate.now());
        settlement.setTotalGrossAmount(new BigDecimal("1800.00"));
        settlement.setTotalCommissionDeducted(new BigDecimal("72.00"));
        settlement.setNetPayoutAmount(new BigDecimal("1728.00"));
        settlement.setBankUtrReference("AXIS-UTR-20261110001");
        settlement.setStatus(SettlementStatus.COMPLETED);
        settlement.setProcessedBy(partnerUser);
        settlement.setProcessedAt(Instant.now());
        settlement.addCommission(commission);
        settlement = settlementRepository.saveAndFlush(settlement);
        assertThat(settlement.getId()).isNotNull();
        assertThat(settlement.getItems()).hasSize(1);

        // 11. Review and Review Report
        Review review = new Review();
        review.setBooking(booking);
        review.setUser(tourist);
        review.setVehicle(vehicle);
        review.setBusiness(business);
        review.setRating(5);
        review.setReviewText("Excellent condition Activa, very smooth rental experience in Havelock!");
        review.setStatus(ReviewStatus.PUBLISHED);
        review = reviewRepository.saveAndFlush(review);
        assertThat(review.getId()).isNotNull();

        Double avgRating = reviewRepository.findAverageRatingByVehicleId(vehicle.getId(), ReviewStatus.PUBLISHED);
        assertThat(avgRating).isEqualTo(5.0);

        // 12. Support Ticket & Discussion Messages
        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNumber("TKT-" + System.currentTimeMillis());
        ticket.setUser(tourist);
        ticket.setBooking(booking);
        ticket.setCategory(TicketCategory.BOOKING);
        ticket.setSubject("Need helmet adjustment at return hub");
        ticket.setDescription("Requesting return location update to Port Blair jetty");
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.addMessage(tourist, "Can I drop off at Phoenix Bay jetty instead?", null, false);
        ticket = supportTicketRepository.saveAndFlush(ticket);
        assertThat(ticket.getId()).isNotNull();
        assertThat(ticket.getMessages()).hasSize(1);

        // 13. Notifications
        Notification notification = new Notification();
        notification.setUser(tourist);
        notification.setTitle("Booking Confirmed!");
        notification.setBody("Your scooter booking BK-" + booking.getBookingReference() + " is confirmed.");
        notification.setChannel(NotificationChannel.IN_APP);
        notification = notificationRepository.saveAndFlush(notification);
        assertThat(notification.getId()).isNotNull();
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(tourist.getId())).isGreaterThanOrEqualTo(1);

        // 14. Audit Log
        AuditLog auditLog = new AuditLog();
        auditLog.setActorUser(tourist);
        auditLog.setAction("BOOKING_CREATED");
        auditLog.setEntityName("Booking");
        auditLog.setEntityId(booking.getId().toString());
        auditLog.setCorrelationId("corr-" + UUID.randomUUID());
        auditLog.setIpAddress("127.0.0.1");
        auditLog = auditLogRepository.saveAndFlush(auditLog);
        assertThat(auditLog.getId()).isNotNull();
    }

    @Test
    @DisplayName("Verify Unique Constraints: Duplicate User Email")
    void testUniqueUserEmailConstraint() {
        String testEmail = "unique.test." + UUID.randomUUID() + "@islandride.in";
        User user1 = new User();
        user1.setEmail(testEmail);
        user1.setFirstName("First");
        user1.setLastName("User");
        user1.setPasswordHash("hash");
        userRepository.saveAndFlush(user1);

        User duplicateUser = new User();
        duplicateUser.setEmail(testEmail);
        duplicateUser.setFirstName("Duplicate");
        duplicateUser.setLastName("User");
        duplicateUser.setPasswordHash("hash");

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicateUser))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Verify Unique Constraints: Duplicate Vehicle Registration Number")
    void testUniqueVehicleRegistrationConstraint() {
        User partnerUser = new User();
        partnerUser.setEmail("reg.partner." + UUID.randomUUID() + "@islandride.in");
        partnerUser.setFirstName("P");
        partnerUser.setLastName("U");
        partnerUser.setPasswordHash("hash");
        partnerUser = userRepository.saveAndFlush(partnerUser);

        Business business = new Business();
        business.setOwnerUser(partnerUser);
        business.setLegalName("Reg Test LLP");
        business.setTradeName("Reg Wheels");
        business = businessRepository.saveAndFlush(business);

        Island island = islandRepository.findAll().get(0);
        VehicleCategory cat = categoryRepository.findAll().get(0);

        String regNum = "AN-01-U" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        Vehicle v1 = new Vehicle();
        v1.setBusiness(business);
        v1.setIsland(island);
        v1.setCategory(cat);
        v1.setMake("Honda");
        v1.setModel("Activa");
        v1.setModelYear(2024);
        v1.setRegistrationNumber(regNum);
        v1.setDailyRate(new BigDecimal("500.00"));
        vehicleRepository.saveAndFlush(v1);

        Vehicle v2 = new Vehicle();
        v2.setBusiness(business);
        v2.setIsland(island);
        v2.setCategory(cat);
        v2.setMake("TVS");
        v2.setModel("Jupiter");
        v2.setModelYear(2024);
        v2.setRegistrationNumber(regNum); // Duplicate registration number
        v2.setDailyRate(new BigDecimal("500.00"));

        assertThatThrownBy(() -> vehicleRepository.saveAndFlush(v2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Verify Soft-Delete on SoftDeletableEntity")
    void testSoftDeleteFunctionality() {
        User user = new User();
        user.setEmail("softdelete." + UUID.randomUUID() + "@islandride.in");
        user.setFirstName("Test");
        user.setLastName("SoftDelete");
        user.setPasswordHash("hash");
        user = userRepository.saveAndFlush(user);

        assertThat(user.isDeleted()).isFalse();
        assertThat(user.getDeletedAt()).isNull();

        user.softDelete();
        user = userRepository.saveAndFlush(user);

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Verify Stage 6A V11/V12 Entities, Multi-Business Staff & Settings Persistence")
    void testStage6AV11AndV12SchemaConstraintsAndEntities() {
        // 1. Verify V8 and V12 system settings seeds exist
        List<String> expectedKeys = List.of(
                "hold.duration.seconds",
                "turnaround.buffer.minutes.default",
                "advance.payment.percentage.default",
                "platform.commission.rate.default",
                "booking.min_rental_hours.default",
                "booking.max_rental_days.default",
                "booking.cancellation.deadline_hours.default",
                "booking.cancellation.free_allowed.default",
                "booking.cancellation.fee_percentage.default",
                "booking.deposit.inspection_window_hours.default",
                "booking.disruption.rescheduling_allowed.default"
        );
        for (String key : expectedKeys) {
            assertThat(systemSettingRepository.findBySettingKey(key))
                    .as("Missing expected system setting key %s", key)
                    .isPresent();
        }

        // 2. Verify multi-business PartnerStaff support (V12 constraint uk_partner_staff_business_user)
        User ownerUser = new User();
        ownerUser.setEmail("v12.owner." + UUID.randomUUID() + "@islandride.in");
        ownerUser.setFirstName("Owner");
        ownerUser.setLastName("Multi");
        ownerUser.setPasswordHash("hash");
        ownerUser = userRepository.saveAndFlush(ownerUser);

        User staffUser = new User();
        staffUser.setEmail("v12.staff." + UUID.randomUUID() + "@islandride.in");
        staffUser.setFirstName("Staff");
        staffUser.setLastName("Multi");
        staffUser.setPasswordHash("hash");
        staffUser = userRepository.saveAndFlush(staffUser);

        Business biz1 = new Business();
        biz1.setOwnerUser(ownerUser);
        biz1.setLegalName("Island Multi Fleet 1");
        biz1.setTradeName("Fleet One");
        biz1.setVerificationStatus(VerificationStatus.APPROVED);
        biz1 = businessRepository.saveAndFlush(biz1);

        Business biz2 = new Business();
        biz2.setOwnerUser(ownerUser);
        biz2.setLegalName("Island Multi Fleet 2");
        biz2.setTradeName("Fleet Two");
        biz2.setVerificationStatus(VerificationStatus.APPROVED);
        biz2 = businessRepository.saveAndFlush(biz2);

        PartnerStaff staffBiz1 = new PartnerStaff();
        staffBiz1.setBusiness(biz1);
        staffBiz1.setUser(staffUser);
        staffBiz1.setRoleInBusiness("MANAGER");
        partnerStaffRepository.saveAndFlush(staffBiz1);

        PartnerStaff staffBiz2 = new PartnerStaff();
        staffBiz2.setBusiness(biz2);
        staffBiz2.setUser(staffUser);
        staffBiz2.setRoleInBusiness("STAFF");
        partnerStaffRepository.saveAndFlush(staffBiz2);

        assertThat(partnerStaffRepository.findByUserId(staffUser.getId())).hasSize(2);

        // 3. Persist booking + V11 payment/deposit/overbooking/webhook/receivable/idempotency entities
        Island island = islandRepository.findAll().get(0);
        Location loc = locationRepository.findAll().get(0);
        VehicleCategory category = categoryRepository.findAll().get(0);

        Vehicle vehicle = new Vehicle();
        vehicle.setBusiness(biz1);
        vehicle.setIsland(island);
        vehicle.setCategory(category);
        vehicle.setMake("Suzuki");
        vehicle.setModel("Access 125");
        vehicle.setModelYear(2025);
        vehicle.setRegistrationNumber("AN-01-V12-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        vehicle.setDailyRate(new BigDecimal("500.00"));
        vehicle.setSecurityDeposit(new BigDecimal("2000.00"));
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle = vehicleRepository.saveAndFlush(vehicle);

        Instant now = Instant.now();
        Booking booking = new Booking();
        booking.setBookingReference("V12-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        booking.setUser(staffUser);
        booking.setVehicle(vehicle);
        booking.setBusiness(biz1);
        booking.setPickupLocation(loc);
        booking.setReturnLocation(loc);
        booking.setStartTime(now.plus(2, ChronoUnit.DAYS));
        booking.setEndTime(now.plus(4, ChronoUnit.DAYS));
        booking.setTotalDays(2);
        booking.setDailyRate(new BigDecimal("500.00"));
        booking.setBaseRentalAmount(new BigDecimal("1000.00"));
        booking.setTaxAmount(new BigDecimal("50.00"));
        booking.setCommissionRateSnapshot(new BigDecimal("4.00"));
        booking.setSecurityDepositAmount(new BigDecimal("2000.00"));
        booking.setTotalPayableAmount(new BigDecimal("1050.00"));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaymentMode(PaymentMode.PARTIAL_ADVANCE);
        booking.setBookingMode(BookingMode.INSTANT);
        booking = bookingRepository.saveAndFlush(booking);

        SecurityDepositRecord deposit = new SecurityDepositRecord();
        deposit.setBooking(booking);
        deposit.setCollectionMethod(SecurityDepositCollectionMethod.AT_PICKUP);
        deposit.setExpectedAmount(new BigDecimal("2000.00"));
        deposit.setCurrency("INR");
        deposit.setStatus(SecurityDepositStatus.PENDING);
        deposit.setInspectionStatus(DepositInspectionStatus.PENDING);
        deposit.setInspectionDeadlineAt(now.plus(5, ChronoUnit.DAYS));
        deposit = securityDepositRecordRepository.saveAndFlush(deposit);
        assertThat(securityDepositRecordRepository.findByBookingId(booking.getId())).isPresent();
        assertThat(deposit.getPublicId()).startsWith("dep_");

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(new BigDecimal("262.50"));
        payment.setCurrency("INR");
        payment.setPaymentType(PaymentType.RENTAL_ADVANCE);
        payment.setDeposit(false);
        payment.setPaymentMethod(PaymentMethod.GATEWAY);
        payment.setGatewayProvider("mock");
        payment.setGatewayOrderId("order_v12_" + UUID.randomUUID().toString().substring(0, 8));
        payment.setGatewayPaymentId("pay_v12_" + UUID.randomUUID().toString().substring(0, 8));
        payment.setGatewaySignature("sig_v12_test");
        payment.setGatewayOrderExpiresAt(now.plus(15, ChronoUnit.MINUTES));
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCapturedAt(now);
        payment.setIdempotencyKey("v12-pay-" + UUID.randomUUID());
        payment.addAttempt(1, "{\"order\":\"mock\"}", "{\"captured\":true}", "SUCCESS", null, null);
        payment = paymentRepository.saveAndFlush(payment);
        assertThat(paymentAttemptRepository.findByPaymentIdOrderByAttemptNumberAsc(payment.getId())).hasSize(1);

        Refund refund = new Refund();
        refund.setPayment(payment);
        refund.setBooking(booking);
        refund.setAmount(new BigDecimal("262.50"));
        refund.setCurrency("INR");
        refund.setReason("DISRUPTION_OVERRIDE");
        refund.setRefundType(RefundType.RENTAL);
        refund.setGatewayFeeRetained(BigDecimal.ZERO);
        refund.setStatus(RefundStatus.PROCESSING);
        refund.setInitiatedBy(ownerUser.getId());
        refund.setApprovedBy(ownerUser.getId());
        refund.setApprovedAt(now);
        refund.setEscalationTicketRef("ESC-1001");
        refund.setIdempotencyKey("v12-ref-" + UUID.randomUUID());
        refund = refundRepository.saveAndFlush(refund);
        assertThat(refund.getPublicId()).startsWith("ref_");

        OverbookingExceptionRecord overbooking = new OverbookingExceptionRecord();
        overbooking.setBooking(booking);
        overbooking.setPaymentId(payment.getId());
        overbooking.setStatus(OverbookingExceptionStatus.DETECTED);
        overbooking = overbookingExceptionRecordRepository.saveAndFlush(overbooking);
        assertThat(overbooking.getPublicId()).startsWith("obx_");

        PaymentWebhookEvent webhook = new PaymentWebhookEvent();
        webhook.setGatewayProvider("mock");
        webhook.setGatewayEventId("evt_" + UUID.randomUUID());
        webhook.setEventType("payment.captured");
        webhook.setSignatureVerified(true);
        webhook.setProcessingStatus(WebhookProcessingStatus.PROCESSED);
        webhook.setBooking(booking);
        webhook.setPayment(payment);
        webhook.setPayloadJson("{\"event\":\"payment.captured\"}");
        webhook.setProcessedAt(now);
        webhook = paymentWebhookEventRepository.saveAndFlush(webhook);
        assertThat(webhook.getPublicId()).startsWith("whk_");

        PartnerReceivable receivable = new PartnerReceivable();
        receivable.setBusiness(biz1);
        receivable.setBooking(booking);
        receivable.setAmountDueFromPartner(new BigDecimal("40.00"));
        receivable.setStatus(ReceivableStatus.OPEN);
        receivable = partnerReceivableRepository.saveAndFlush(receivable);
        assertThat(receivable.getPublicId()).startsWith("rcv_");

        IdempotencyRecord idem = new IdempotencyRecord();
        idem.setScope("TEST_SCHEMA");
        idem.setActorKey("usr:" + staffUser.getId());
        idem.setIdempotencyKey("key-" + UUID.randomUUID());
        idem.setRequestHash("a".repeat(64));
        idem.setStatus(IdempotencyStatus.COMPLETED);
        idem.setHttpStatus(201);
        idem.setResourcePublicId("res_123");
        idem.setResponseBody("{\"ok\":true}");
        idem.setExpiresAt(now.plus(1, ChronoUnit.DAYS));
        idem = idempotencyRecordRepository.saveAndFlush(idem);
        assertThat(idem.getId()).isNotNull();

        // 4. Duplicate membership within the same business is still blocked by uk_partner_staff_business_user
        // (tested last because a constraint-violation flush invalidates the active Hibernate session)
        PartnerStaff duplicateSameBiz = new PartnerStaff();
        duplicateSameBiz.setBusiness(biz1);
        duplicateSameBiz.setUser(staffUser);
        duplicateSameBiz.setRoleInBusiness("STAFF");
        assertThatThrownBy(() -> partnerStaffRepository.saveAndFlush(duplicateSameBiz))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
