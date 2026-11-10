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
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.partner.repository.BusinessRepository;
import com.islandride.paymentfinance.domain.*;
import com.islandride.paymentfinance.repository.OfflineCollectionRepository;
import com.islandride.paymentfinance.repository.PaymentRepository;
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
}
