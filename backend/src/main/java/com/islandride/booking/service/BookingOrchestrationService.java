package com.islandride.booking.service;

import com.islandride.administration.service.AuditLogService;
import com.islandride.availability.domain.HoldStatus;
import com.islandride.availability.domain.InventoryHold;
import com.islandride.availability.repository.InventoryHoldRepository;
import com.islandride.availability.service.InventoryHoldService;
import com.islandride.booking.domain.Booking;
import com.islandride.inventory.domain.BookingMode;
import com.islandride.booking.domain.BookingModification;
import com.islandride.booking.domain.BookingStatus;
import com.islandride.booking.domain.BookingStatusHistory;
import com.islandride.booking.domain.DepositInspectionStatus;
import com.islandride.booking.domain.DisruptionExceptionType;
import com.islandride.booking.domain.HandoverEvidence;
import com.islandride.booking.domain.HandoverInspection;
import com.islandride.booking.domain.HandoverType;
import com.islandride.booking.domain.OverbookingExceptionRecord;
import com.islandride.booking.domain.OverbookingExceptionStatus;
import com.islandride.booking.domain.PaymentMode;
import com.islandride.booking.domain.PaymentSummaryStatus;
import com.islandride.booking.domain.SecurityDepositCollectionMethod;
import com.islandride.booking.domain.SecurityDepositRecord;
import com.islandride.booking.domain.SecurityDepositStatus;
import com.islandride.booking.dto.BookingDtos;
import com.islandride.booking.repository.BookingModificationRepository;
import com.islandride.booking.repository.BookingRepository;
import com.islandride.booking.repository.BookingStatusHistoryRepository;
import com.islandride.booking.repository.HandoverInspectionRepository;
import com.islandride.booking.repository.OverbookingExceptionRecordRepository;
import com.islandride.booking.repository.SecurityDepositRecordRepository;
import com.islandride.commission.service.CommissionCalculationService;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.ConflictException;
import com.islandride.core.exception.IdempotencyConflictException;
import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.core.idempotency.IdempotencyService;
import com.islandride.core.security.TenantSecurityService;
import com.islandride.core.security.UserPrincipal;
import com.islandride.geography.domain.Location;
import com.islandride.geography.repository.LocationRepository;
import com.islandride.identityaccess.domain.User;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.inventory.domain.AvailabilityBlockStatus;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.domain.VehicleStatus;
import com.islandride.inventory.domain.VehicleUnavailability;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.inventory.repository.VehicleUnavailabilityRepository;
import com.islandride.notifications.service.NotificationDispatchService;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.partner.repository.PartnerStaffRepository;
import com.islandride.paymentfinance.domain.OfflineCollection;
import com.islandride.paymentfinance.domain.Payment;
import com.islandride.paymentfinance.domain.PaymentMethod;
import com.islandride.paymentfinance.domain.PaymentStatus;
import com.islandride.paymentfinance.domain.PaymentType;
import com.islandride.paymentfinance.domain.Refund;
import com.islandride.paymentfinance.domain.RefundType;
import com.islandride.paymentfinance.dto.PaymentDtos.GatewayCheckoutOrderResponse;
import com.islandride.paymentfinance.repository.OfflineCollectionRepository;
import com.islandride.paymentfinance.repository.PaymentRepository;
import com.islandride.paymentfinance.repository.RefundRepository;
import com.islandride.paymentfinance.service.PaymentOrchestrationService;
import com.islandride.paymentfinance.service.RefundOrchestrationService;
import com.islandride.pricing.service.AuthoritativePricingService;
import com.islandride.pricing.service.BookingPolicyService;
import com.islandride.pricing.service.EffectiveBookingPolicy;
import com.islandride.pricing.service.OperatingHoursService;
import com.islandride.pricing.service.OperatingHoursValidationResult;
import com.islandride.pricing.service.PricingSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(BookingOrchestrationService.class);

    private static final List<String> NON_BLOCKING_BOOKING_STATUS_NAMES = BookingStatus.NON_BLOCKING_STATUSES.stream()
            .map(Enum::name)
            .toList();

    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final InventoryHoldRepository holdRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final BookingStatusHistoryRepository statusHistoryRepository;
    private final HandoverInspectionRepository handoverRepository;
    private final SecurityDepositRecordRepository depositRepository;
    private final OverbookingExceptionRecordRepository overbookingRepository;
    private final BookingModificationRepository modificationRepository;
    private final OfflineCollectionRepository offlineCollectionRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final VehicleUnavailabilityRepository unavailabilityRepository;

    private final InventoryHoldService holdService;
    private final AuthoritativePricingService pricingService;
    private final BookingPolicyService bookingPolicyService;
    private final OperatingHoursService operatingHoursService;
    private final CancellationSnapshotService cancellationSnapshotService;
    private final BookingLifecycleFoundationService lifecycleService;
    private final CommissionCalculationService commissionCalculationService;
    private final PaymentOrchestrationService paymentOrchestrationService;
    private final RefundOrchestrationService refundOrchestrationService;
    private final TenantSecurityService tenantSecurityService;
    private final NotificationDispatchService notificationDispatchService;
    private final AuditLogService auditLogService;
    private final BookingDtoMapper dtoMapper;
    private final IdempotencyService idempotencyService;
    private final TransactionTemplate transactionTemplate;

    private final PartnerStaffRepository partnerStaffRepository;

    public BookingOrchestrationService(BookingRepository bookingRepository,
                                       BookingStatusHistoryRepository statusHistoryRepository,
                                       VehicleRepository vehicleRepository,
                                       InventoryHoldRepository holdRepository,
                                       LocationRepository locationRepository,
                                       UserRepository userRepository,
                                       PartnerStaffRepository partnerStaffRepository,
                                       HandoverInspectionRepository handoverRepository,
                                       SecurityDepositRecordRepository depositRepository,
                                       OverbookingExceptionRecordRepository overbookingRepository,
                                       BookingModificationRepository modificationRepository,
                                       OfflineCollectionRepository offlineCollectionRepository,
                                       PaymentRepository paymentRepository,
                                       RefundRepository refundRepository,
                                       VehicleUnavailabilityRepository unavailabilityRepository,
                                       InventoryHoldService holdService,
                                       AuthoritativePricingService pricingService,
                                       BookingPolicyService bookingPolicyService,
                                       OperatingHoursService operatingHoursService,
                                       CancellationSnapshotService cancellationSnapshotService,
                                       BookingLifecycleFoundationService lifecycleService,
                                       CommissionCalculationService commissionCalculationService,
                                       PaymentOrchestrationService paymentOrchestrationService,
                                       RefundOrchestrationService refundOrchestrationService,
                                       TenantSecurityService tenantSecurityService,
                                       NotificationDispatchService notificationDispatchService,
                                       AuditLogService auditLogService,
                                       BookingDtoMapper dtoMapper,
                                       IdempotencyService idempotencyService,
                                       PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.vehicleRepository = vehicleRepository;
        this.holdRepository = holdRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
        this.partnerStaffRepository = partnerStaffRepository;
        this.handoverRepository = handoverRepository;
        this.depositRepository = depositRepository;
        this.overbookingRepository = overbookingRepository;
        this.modificationRepository = modificationRepository;
        this.offlineCollectionRepository = offlineCollectionRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.unavailabilityRepository = unavailabilityRepository;
        this.holdService = holdService;
        this.pricingService = pricingService;
        this.bookingPolicyService = bookingPolicyService;
        this.operatingHoursService = operatingHoursService;
        this.cancellationSnapshotService = cancellationSnapshotService;
        this.lifecycleService = lifecycleService;
        this.commissionCalculationService = commissionCalculationService;
        this.paymentOrchestrationService = paymentOrchestrationService;
        this.refundOrchestrationService = refundOrchestrationService;
        this.tenantSecurityService = tenantSecurityService;
        this.notificationDispatchService = notificationDispatchService;
        this.auditLogService = auditLogService;
        this.dtoMapper = dtoMapper;
        this.idempotencyService = idempotencyService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public record BookingCreationResult(
            BookingDtos.BookingDetailDto booking,
            GatewayCheckoutOrderResponse checkoutOrder
    ) {
    }

    private record CreateBookingIdempotencyPayload(
            String holdPublicId,
            PaymentMode paymentMode,
            boolean deliveryRequested,
            String customerDlNumber,
            String customerEmergencyContact,
            boolean dlConfirmed
    ) {
    }

    @Transactional(readOnly = true)
    public BookingDtos.InventoryHoldResponse getTouristHold(Long touristUserId, String holdPublicId) {
        InventoryHold hold = holdRepository.findByPublicId(holdPublicId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory hold not found: " + holdPublicId));
        if (!hold.getUser().getId().equals(touristUserId)) {
            throw new ResourceNotFoundException("Inventory hold not found: " + holdPublicId);
        }
        Instant now = Instant.now();
        Vehicle vehicle = hold.getVehicle();
        EffectiveBookingPolicy policy = bookingPolicyService.resolvePolicy(vehicle);
        boolean activeForBooking = hold.getStatus() == HoldStatus.HELD && hold.getExpiresAt().isAfter(now);
        String conflictReason = activeForBooking ? null : "Hold is in status " + hold.getStatus() + " or has expired.";

        PricingSnapshot fullSnap = pricingService.calculateQuote(vehicle, hold.getStartTime(), hold.getEndTime(), PaymentMode.FULL, false, now);
        PricingSnapshot partialSnap = pricingService.calculateQuote(vehicle, hold.getStartTime(), hold.getEndTime(), PaymentMode.PARTIAL_ADVANCE, false, now);

        Location pickup = hold.getPickupLocation() != null ? hold.getPickupLocation() : vehicle.getPickupLocation();
        Location ret = hold.getReturnLocation() != null ? hold.getReturnLocation() : vehicle.getReturnLocation();

        long remainingSeconds = Math.max(0L, Duration.between(now, hold.getExpiresAt()).getSeconds());

        return new BookingDtos.InventoryHoldResponse(
                hold.getPublicId(),
                vehicle.getPublicId(),
                vehicle.getTitle(),
                vehicle.getBusiness().getTradeName(),
                vehicle.getIsland().getName(),
                vehicle.getBookingMode() != null ? vehicle.getBookingMode() : BookingMode.INSTANT,
                hold.getHoldPurpose(),
                hold.getStatus(),
                pickup != null ? pickup.getPublicId() : null,
                pickup != null ? pickup.getName() : null,
                ret != null ? ret.getPublicId() : null,
                ret != null ? ret.getName() : null,
                hold.getStartTime(),
                hold.getEndTime(),
                hold.getTurnaroundBufferMinutes(),
                hold.getHoldDurationSeconds(),
                hold.getExpiresAt(),
                remainingSeconds,
                toPricePreview(vehicle, hold, pickup, ret, fullSnap, policy, activeForBooking, conflictReason),
                toPricePreview(vehicle, hold, pickup, ret, partialSnap, policy, activeForBooking, conflictReason)
        );
    }

    private BookingDtos.PricePreviewResponse toPricePreview(Vehicle vehicle,
                                                            InventoryHold hold,
                                                            Location pickup,
                                                            Location ret,
                                                            PricingSnapshot snapshot,
                                                            EffectiveBookingPolicy policy,
                                                            boolean available,
                                                            String conflictReason) {
        return new BookingDtos.PricePreviewResponse(
                vehicle.getPublicId(),
                vehicle.getTitle(),
                vehicle.getBookingMode() != null ? vehicle.getBookingMode() : BookingMode.INSTANT,
                available,
                conflictReason,
                hold.getStartTime(),
                hold.getEndTime(),
                pickup != null ? pickup.getPublicId() : null,
                pickup != null ? pickup.getName() : null,
                ret != null ? ret.getPublicId() : null,
                ret != null ? ret.getName() : null,
                snapshot.currency(),
                snapshot.billingUnit(),
                snapshot.billableDays(),
                snapshot.billableHours(),
                snapshot.dailyRate(),
                snapshot.hourlyRate(),
                snapshot.baseRentalAmount(),
                snapshot.seasonalMultiplier(),
                snapshot.seasonalAdjustmentAmount(),
                snapshot.discountAmount(),
                snapshot.surchargeAmount(),
                snapshot.cleaningFee(),
                snapshot.deliveryFee(),
                snapshot.gstPercentage(),
                snapshot.taxAmount(),
                snapshot.totalPayableAmount(),
                snapshot.securityDepositAmount(),
                snapshot.depositCollectionMethod(),
                snapshot.requestedPaymentMode(),
                snapshot.enforcedPaymentMode(),
                snapshot.advancePercentageApplied(),
                snapshot.advancePaidDueNow(),
                snapshot.pickupBalanceDue(),
                snapshot.turnaroundBufferMinutes(),
                snapshot.fullPaymentCutoffHours(),
                policy.holdDurationSeconds(),
                policy.cancellationDeadlineHours(),
                policy.freeCancellationAllowed(),
                policy.cancellationFeePercentage()
        );
    }

    public BookingCreationResult createBookingFromHold(Long touristUserId,
                                                       BookingDtos.CreateBookingRequest request) {
        if (request.customerDlNumber() == null || request.customerDlNumber().isBlank()) {
            throw new BusinessRuleException("Customer driving license number is required.");
        }
        if (request.customerEmergencyContact() == null || request.customerEmergencyContact().isBlank()) {
            throw new BusinessRuleException("Emergency contact number is required.");
        }
        if (!request.dlConfirmed()) {
            throw new BusinessRuleException("Driving license validity confirmation is required.");
        }

        String effectiveIdempotencyKey = (request.idempotencyKey() != null && !request.idempotencyKey().isBlank())
                ? request.idempotencyKey().trim()
                : "bkg_" + request.holdPublicId().trim();

        var canonicalPayload = new CreateBookingIdempotencyPayload(
                request.holdPublicId().trim(),
                request.paymentMode() != null ? request.paymentMode() : PaymentMode.FULL,
                request.deliveryRequested(),
                request.customerDlNumber().trim(),
                request.customerEmergencyContact().trim(),
                request.dlConfirmed()
        );

        return lifecycleService.executeIdempotentCommand(
                "BOOKING_CREATE",
                IdempotencyService.actorKeyForUser(touristUserId),
                effectiveIdempotencyKey,
                canonicalPayload,
                Duration.ofHours(24),
                BookingCreationResult.class,
                () -> transactionTemplate.execute(txStatus -> {
                    BookingCreationResult created = executeCreateBookingFromHoldTx(touristUserId, request, effectiveIdempotencyKey);
                    return new IdempotencyService.RecordedPayload<>(201, created.booking().summary().publicId(), created);
                })
        ).body();
    }

    private BookingCreationResult executeCreateBookingFromHoldTx(Long touristUserId,
                                                                 BookingDtos.CreateBookingRequest request,
                                                                 String effectiveIdempotencyKey) {
        InventoryHold hold = holdRepository.findByPublicId(request.holdPublicId().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory hold not found: " + request.holdPublicId()));
        if (!hold.getUser().getId().equals(touristUserId)) {
            throw new ResourceNotFoundException("Inventory hold not found: " + request.holdPublicId());
        }

        Instant now = Instant.now();
        // Canonical lock order: Vehicle (PESSIMISTIC_WRITE) -> Booking -> Hold
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(hold.getVehicle().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found for hold."));

        if (vehicle.getStatus() != VehicleStatus.ACTIVE
                || vehicle.getBusiness().isDeleted()
                || vehicle.getBusiness().getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new BusinessRuleException("Vehicle or partner is no longer active for new bookings.");
        }

        // Atomically transition HOLD -> CONVERTED
        holdService.convertHoldToBookingOrFail(hold.getId(), touristUserId, now);

        EffectiveBookingPolicy policy = bookingPolicyService.resolvePolicy(vehicle);
        int bufferMinutes = policy.turnaroundBufferMinutes();

        List<Booking> overlappingBookings = bookingRepository.findOverlappingBookingsWithBuffer(
                vehicle.getId(), hold.getStartTime(), hold.getEndTime(), bufferMinutes, NON_BLOCKING_BOOKING_STATUS_NAMES);
        if (!overlappingBookings.isEmpty()) {
            throw new ConflictException("Vehicle is no longer available for the selected window (conflicts with another booking or turnaround buffer).");
        }

        List<InventoryHold> otherHolds = holdRepository.findActiveOverlappingHoldsWithBuffer(
                        vehicle.getId(), hold.getStartTime(), hold.getEndTime(), bufferMinutes, now).stream()
                .filter(h -> !h.getId().equals(hold.getId()))
                .toList();
        if (!otherHolds.isEmpty()) {
            throw new ConflictException("Vehicle currently has another active hold overlapping this window.");
        }

        List<VehicleUnavailability> blocks = unavailabilityRepository.findOverlappingBlocks(
                vehicle.getId(), hold.getStartTime(), hold.getEndTime(), AvailabilityBlockStatus.ACTIVE);
        if (!blocks.isEmpty()) {
            throw new ConflictException("Vehicle is blocked for maintenance or manual unavailability during this window.");
        }

        User touristUser = userRepository.findById(touristUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + touristUserId));

        PaymentMode requestedMode = request.paymentMode() != null ? request.paymentMode() : PaymentMode.FULL;
        PricingSnapshot pricing = pricingService.calculateQuote(
                vehicle,
                hold.getStartTime(),
                hold.getEndTime(),
                requestedMode,
                request.deliveryRequested(),
                now
        );

        CancellationPolicySnapshot cancellationSnapshot = cancellationSnapshotService.captureSnapshot(
                vehicle.getBusiness().getId(),
                pricing.securityDepositAmount(),
                now
        );

        BookingMode effectiveBookingMode = vehicle.getBookingMode() != null ? vehicle.getBookingMode() : BookingMode.INSTANT;
        BookingStatus initialStatus = effectiveBookingMode == BookingMode.APPROVAL
                ? BookingStatus.AWAITING_PARTNER_APPROVAL
                : BookingStatus.PENDING_PAYMENT;

        Instant approvalExpiresAt = null;
        Instant paymentDeadlineAt = null;
        if (effectiveBookingMode == BookingMode.APPROVAL) {
            approvalExpiresAt = policy.computeApprovalExpiresAt(now, hold.getStartTime());
        } else {
            paymentDeadlineAt = bookingPolicyService.computeInstantPaymentDeadline(now, hold.getStartTime());
        }

        String reference = "IR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);

        Booking booking = new Booking();
        booking.setBookingReference(reference);
        booking.setUser(touristUser);
        booking.setVehicle(vehicle);
        booking.setBusiness(vehicle.getBusiness());
        booking.setInventoryHold(hold);
        booking.setPickupLocation(hold.getPickupLocation() != null ? hold.getPickupLocation() : vehicle.getPickupLocation());
        booking.setReturnLocation(hold.getReturnLocation() != null ? hold.getReturnLocation() : vehicle.getReturnLocation());
        booking.setStartTime(hold.getStartTime());
        booking.setEndTime(hold.getEndTime());
        booking.setTotalDays(pricing.billableDays());
        booking.setTotalHours(pricing.billableHours());
        booking.setDailyRate(pricing.dailyRate());
        booking.setHourlyRate(pricing.hourlyRate());
        booking.setWeeklyRate(pricing.weeklyRate());
        booking.setPricingPlanPublicId(pricing.pricingPlanPublicId());
        booking.setCurrency(pricing.currency());
        booking.setBaseRentalAmount(pricing.baseRentalAmount());
        booking.setSeasonalMultiplier(pricing.seasonalMultiplier());
        booking.setSeasonalAdjustmentAmount(pricing.seasonalAdjustmentAmount());
        booking.setDiscountAmount(pricing.discountAmount());
        booking.setSurchargeAmount(pricing.surchargeAmount());
        booking.setCleaningFee(pricing.cleaningFee());
        booking.setDeliveryFee(pricing.deliveryFee());
        booking.setTaxAmount(pricing.taxAmount());
        booking.setGstPercentage(pricing.gstPercentage());
        booking.setCommissionRateSnapshot(pricing.commissionRateSnapshot());
        booking.setSecurityDepositAmount(pricing.securityDepositAmount());
        booking.setDepositCollectionMethod(pricing.depositCollectionMethod());
        booking.setTotalPayableAmount(pricing.totalPayableAmount());
        booking.setAdvancePaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        booking.setPickupBalanceDue(pricing.pickupBalanceDue());
        booking.setAdvancePercentageApplied(pricing.advancePercentageApplied());
        booking.setTurnaroundBufferMinutes(pricing.turnaroundBufferMinutes());
        booking.setBookingMode(effectiveBookingMode);
        booking.setPaymentMode(pricing.enforcedPaymentMode());
        booking.setStatus(initialStatus);
        booking.setPaymentSummaryStatus(PaymentSummaryStatus.UNPAID);
        booking.setOverbookingExceptionStatus(OverbookingExceptionStatus.NONE);
        booking.setCustomerDlNumber(request.customerDlNumber().trim());
        booking.setCustomerEmergencyContact(request.customerEmergencyContact().trim());
        booking.setDlConfirmed(true);
        booking.setApprovalExpiresAt(approvalExpiresAt);
        booking.setPaymentDeadlineAt(paymentDeadlineAt);
        booking.setIdempotencyKey(effectiveIdempotencyKey);
        booking.setPricingSnapshotJson(pricingService.serialize(pricing));
        booking.setCancellationPolicySnapshot(cancellationSnapshotService.serialize(cancellationSnapshot));

        booking = bookingRepository.save(booking);

        BookingStatusHistory initialHistory = new BookingStatusHistory();
        initialHistory.setBooking(booking);
        initialHistory.setFromStatus(null);
        initialHistory.setToStatus(initialStatus.name());
        initialHistory.setChangeReason("Booking created from hold " + hold.getPublicId());
        initialHistory.setChangedBy(touristUserId);
        statusHistoryRepository.save(initialHistory);

        SecurityDepositRecord depositRecord = new SecurityDepositRecord();
        depositRecord.setBooking(booking);
        depositRecord.setCollectionMethod(pricing.depositCollectionMethod());
        depositRecord.setExpectedAmount(pricing.securityDepositAmount());
        depositRecord.setCollectedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        depositRecord.setRefundedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        depositRecord.setForfeitedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        depositRecord.setCurrency(pricing.currency());
        depositRecord.setStatus(pricing.securityDepositAmount().compareTo(BigDecimal.ZERO) == 0 ? SecurityDepositStatus.WAIVED : SecurityDepositStatus.PENDING);
        depositRecord.setInspectionStatus(DepositInspectionStatus.PENDING);
        depositRepository.save(depositRecord);

        GatewayCheckoutOrderResponse checkoutOrder = null;
        if (effectiveBookingMode == BookingMode.INSTANT) {
            checkoutOrder = paymentOrchestrationService.createOrderOnLockedBooking(
                    booking,
                    touristUserId,
                    "order_" + effectiveIdempotencyKey,
                    now
            );
        } else {
            notifyPartnerForApproval(booking);
        }

        auditLogService.record(
                touristUserId,
                "BOOKING_CREATED",
                "Booking",
                booking.getPublicId(),
                null,
                Map.of(
                        "bookingReference", booking.getBookingReference(),
                        "bookingMode", booking.getBookingMode().name(),
                        "status", booking.getStatus().name()
                )
        );

        return new BookingCreationResult(dtoMapper.toDetail(booking), checkoutOrder);
    }

    @Transactional
    public BookingDtos.BookingDetailDto partnerApproveBooking(Long partnerUserId,
                                                             String bookingPublicId,
                                                             BookingDtos.PartnerApprovalDecisionRequest req) {
        Booking booking = requireLockedBookingForPartnerWrite(bookingPublicId, partnerUserId, true);
        Instant now = Instant.now();
        EffectiveBookingPolicy policy = bookingPolicyService.resolvePolicy(booking.getVehicle());
        Instant paymentDeadline = policy.computePostApprovalPaymentDeadline(now, booking.getStartTime());
        booking.setPaymentDeadlineAt(paymentDeadline);
        booking.setApprovedAt(now);
        booking.setApprovedBy(partnerUserId);

        lifecycleService.transitionStatus(
                booking,
                BookingStatus.APPROVED,
                BookingActorRole.PARTNER,
                partnerUserId,
                req != null && req.reason() != null ? req.reason() : "Partner approved reservation request",
                now
        );

        if (booking.getUser() != null) {
            notificationDispatchService.sendInAppNotification(
                    booking.getUser().getId(),
                    "Booking Approved: " + booking.getBookingReference(),
                    "Your booking request was approved! Complete payment before " + paymentDeadline + " to confirm your vehicle.",
                    "/tourist/bookings/" + booking.getPublicId()
            );
        }
        return dtoMapper.toDetail(booking);
    }

    @Transactional
    public BookingDtos.BookingDetailDto partnerRejectBooking(Long partnerUserId,
                                                            String bookingPublicId,
                                                            BookingDtos.PartnerApprovalDecisionRequest req) {
        Booking booking = requireLockedBookingForPartnerWrite(bookingPublicId, partnerUserId, true);
        Instant now = Instant.now();
        String reason = (req != null && req.reason() != null && !req.reason().isBlank())
                ? req.reason().trim()
                : "Partner declined booking request";

        lifecycleService.transitionStatus(
                booking,
                BookingStatus.REJECTED,
                BookingActorRole.PARTNER,
                partnerUserId,
                reason,
                now
        );

        waivePreHandoverDeposit(booking, "Booking rejected by partner");

        if (booking.getUser() != null) {
            notificationDispatchService.sendInAppNotification(
                    booking.getUser().getId(),
                    "Booking Declined: " + booking.getBookingReference(),
                    "Your booking request was declined by the partner: " + reason,
                    "/tourist/bookings/" + booking.getPublicId()
            );
        }
        return dtoMapper.toDetail(booking);
    }

    @Transactional
    public BookingDtos.BookingDetailDto modifyBooking(Long touristUserId,
                                                      String bookingPublicId,
                                                      BookingDtos.ModifyBookingRequest request) {
        Booking booking = bookingRepository.findByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingPublicId));
        if (!booking.getUser().getId().equals(touristUserId)) {
            throw new ResourceNotFoundException("Booking not found: " + bookingPublicId);
        }

        // Modifications only permitted for CONFIRMED, APPROVED or PENDING_PAYMENT
        if (booking.getStatus() != BookingStatus.CONFIRMED
                && booking.getStatus() != BookingStatus.APPROVED
                && booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("Booking in status " + booking.getStatus() + " cannot be modified/rescheduled.");
        }

        Instant now = Instant.now();
        int cutoffHours = bookingPolicyService.resolveModificationCutoffHours();
        if (now.plus(Duration.ofHours(cutoffHours)).isAfter(booking.getStartTime())) {
            throw new BusinessRuleException("Modifications are cut off within " + cutoffHours + " hours of pickup start.");
        }

        // Canonical lock order: Vehicle -> Booking
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(booking.getVehicle().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found."));
        booking = bookingRepository.findByIdForUpdate(booking.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found."));

        Location requestedPickup = request.pickupLocationPublicId() != null
                ? resolveLocation(request.pickupLocationPublicId())
                : booking.getPickupLocation();
        Location requestedReturn = request.returnLocationPublicId() != null
                ? resolveLocation(request.returnLocationPublicId())
                : booking.getReturnLocation();

        OperatingHoursValidationResult validatedLocs = operatingHoursService.validateLocationsAndOperatingHours(
                vehicle,
                requestedPickup,
                requestedReturn,
                request.newStartTime(),
                request.newEndTime()
        );

        int bufferMinutes = booking.getTurnaroundBufferMinutes();
        List<Booking> conflicts = bookingRepository.findOverlappingBookingsWithBufferExcludingBooking(
                vehicle.getId(),
                booking.getId(),
                request.newStartTime(),
                request.newEndTime(),
                bufferMinutes,
                NON_BLOCKING_BOOKING_STATUS_NAMES
        );
        if (!conflicts.isEmpty()) {
            throw new ConflictException("Rescheduled timeframe conflicts with another confirmed booking or buffer.");
        }

        List<InventoryHold> conflictingHolds = holdRepository.findActiveOverlappingHoldsWithBuffer(
                vehicle.getId(),
                request.newStartTime(),
                request.newEndTime(),
                bufferMinutes,
                now
        );
        if (!conflictingHolds.isEmpty()) {
            throw new ConflictException("Rescheduled timeframe conflicts with an active temporary checkout hold.");
        }

        List<VehicleUnavailability> blocks = unavailabilityRepository.findOverlappingBlocks(
                vehicle.getId(),
                request.newStartTime(),
                request.newEndTime(),
                AvailabilityBlockStatus.ACTIVE
        );
        if (!blocks.isEmpty()) {
            throw new ConflictException("Rescheduled timeframe conflicts with a maintenance or manual schedule block.");
        }

        boolean deliveryRequested = request.deliveryRequested() != null
                ? request.deliveryRequested()
                : (booking.getDeliveryFee() != null && booking.getDeliveryFee().compareTo(BigDecimal.ZERO) > 0);

        PricingSnapshot newPricing = pricingService.calculateQuote(
                vehicle,
                request.newStartTime(),
                request.newEndTime(),
                booking.getPaymentMode(),
                deliveryRequested,
                now
        );

        BigDecimal previousTotal = booking.getTotalPayableAmount();
        BigDecimal newTotal = newPricing.totalPayableAmount();
        BigDecimal priceDelta = newTotal.subtract(previousTotal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentAdvancePaid = booking.getAdvancePaidAmount() != null ? booking.getAdvancePaidAmount() : BigDecimal.ZERO;

        String adjustmentType = "NONE";
        if (priceDelta.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal newPickupDue = newTotal.subtract(currentAdvancePaid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            booking.setPickupBalanceDue(newPickupDue);
            adjustmentType = "PICKUP_BALANCE_UPDATED";
        } else if (priceDelta.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal deltaMagnitude = priceDelta.abs();
            BigDecimal currentPickupDue = booking.getPickupBalanceDue() != null ? booking.getPickupBalanceDue() : BigDecimal.ZERO;
            if (currentPickupDue.compareTo(deltaMagnitude) >= 0) {
                booking.setPickupBalanceDue(currentPickupDue.subtract(deltaMagnitude).setScale(2, RoundingMode.HALF_UP));
                adjustmentType = "PICKUP_BALANCE_UPDATED";
            } else {
                BigDecimal remainingRefundCredit = deltaMagnitude.subtract(currentPickupDue).setScale(2, RoundingMode.HALF_UP);
                booking.setPickupBalanceDue(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                Payment capturedAdvancePayment = paymentRepository.findByBookingIdOrderByCreatedAtDesc(booking.getId()).stream()
                        .filter(p -> p.getStatus() == PaymentStatus.CAPTURED && !p.isDeposit())
                        .findFirst()
                        .orElse(null);
                if (capturedAdvancePayment != null && remainingRefundCredit.compareTo(BigDecimal.ZERO) > 0) {
                    refundOrchestrationService.initiateAndExecuteRefund(
                            booking,
                            capturedAdvancePayment,
                            remainingRefundCredit,
                            BigDecimal.ZERO,
                            RefundType.PARTIAL_ADJUSTMENT,
                            "Partial refund for booking reschedule price reduction",
                            touristUserId,
                            "mod_ref_" + booking.getPublicId() + "_" + UUID.randomUUID().toString().substring(0, 8),
                            now
                    );
                    booking.setAdvancePaidAmount(currentAdvancePaid.subtract(remainingRefundCredit).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
                    adjustmentType = "PARTIAL_REFUND";
                } else {
                    adjustmentType = "PICKUP_BALANCE_UPDATED";
                }
            }
        }

        BookingModification mod = new BookingModification();
        mod.setBooking(booking);
        mod.setModifiedByUserId(touristUserId);
        mod.setPreviousStartTime(booking.getStartTime());
        mod.setPreviousEndTime(booking.getEndTime());
        mod.setNewStartTime(request.newStartTime());
        mod.setNewEndTime(request.newEndTime());
        mod.setPreviousPickupLocation(booking.getPickupLocation());
        mod.setPreviousReturnLocation(booking.getReturnLocation());
        mod.setNewPickupLocation(validatedLocs.resolvedPickupLocation());
        mod.setNewReturnLocation(validatedLocs.resolvedReturnLocation());
        mod.setPreviousTotalPayable(previousTotal);
        mod.setNewTotalPayable(newTotal);
        mod.setPriceDelta(priceDelta);
        mod.setAdjustmentType(adjustmentType);
        mod.setReason(request.reason() != null && !request.reason().isBlank() ? request.reason().trim() : "Tourist rescheduled reservation");
        modificationRepository.save(mod);

        booking.setStartTime(request.newStartTime());
        booking.setEndTime(request.newEndTime());
        booking.setPickupLocation(validatedLocs.resolvedPickupLocation());
        booking.setReturnLocation(validatedLocs.resolvedReturnLocation());
        booking.setTotalDays(newPricing.billableDays());
        booking.setTotalHours(newPricing.billableHours());
        booking.setDailyRate(newPricing.dailyRate());
        booking.setHourlyRate(newPricing.hourlyRate());
        booking.setWeeklyRate(newPricing.weeklyRate());
        booking.setBaseRentalAmount(newPricing.baseRentalAmount());
        booking.setSeasonalMultiplier(newPricing.seasonalMultiplier());
        booking.setSeasonalAdjustmentAmount(newPricing.seasonalAdjustmentAmount());
        booking.setDiscountAmount(newPricing.discountAmount());
        booking.setSurchargeAmount(newPricing.surchargeAmount());
        booking.setCleaningFee(newPricing.cleaningFee());
        booking.setDeliveryFee(newPricing.deliveryFee());
        booking.setTaxAmount(newPricing.taxAmount());
        booking.setGstPercentage(newPricing.gstPercentage());
        booking.setCommissionRateSnapshot(newPricing.commissionRateSnapshot());
        booking.setTotalPayableAmount(newTotal);
        booking.setSecurityDepositAmount(newPricing.securityDepositAmount());
        booking.setPricingSnapshotJson(pricingService.serialize(newPricing));

        bookingRepository.save(booking);

        auditLogService.record(
                touristUserId,
                "BOOKING_MODIFIED",
                "Booking",
                booking.getPublicId(),
                null,
                Map.of(
                        "modificationPublicId", mod.getPublicId(),
                        "priceDelta", priceDelta.toPlainString(),
                        "adjustmentType", adjustmentType
                )
        );
        return dtoMapper.toDetail(booking);
    }

    @Transactional(readOnly = true)
    public BookingDtos.CancellationPreviewResponse previewCancellation(Long touristUserId, String bookingPublicId) {
        Booking booking = bookingRepository.findByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingPublicId));
        if (!booking.getUser().getId().equals(touristUserId)) {
            throw new ResourceNotFoundException("Booking not found: " + bookingPublicId);
        }
        return evaluateCancellationBreakdown(booking, Instant.now());
    }

    @Transactional
    public BookingDtos.BookingDetailDto cancelBookingByTourist(Long touristUserId,
                                                              String bookingPublicId,
                                                              BookingDtos.CancelBookingRequest request) {
        Booking booking = bookingRepository.findByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingPublicId));
        if (!booking.getUser().getId().equals(touristUserId)) {
            throw new ResourceNotFoundException("Booking not found: " + bookingPublicId);
        }

        // Canonical lock order: Vehicle -> Booking
        vehicleRepository.findByIdForUpdate(booking.getVehicle().getId());
        booking = bookingRepository.findByIdForUpdate(booking.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found."));

        Instant now = Instant.now();
        BookingDtos.CancellationPreviewResponse preview = evaluateCancellationBreakdown(booking, now);

        lifecycleService.transitionStatus(
                booking,
                BookingStatus.CANCELLED,
                BookingActorRole.TOURIST,
                touristUserId,
                request != null && request.reason() != null ? request.reason() : "Cancelled by tourist",
                now
        );
        booking.setCancelledAt(now);
        booking.setCancelledBy(touristUserId);
        booking.setCancelledByRole("TOURIST");
        booking.setCancellationReason(request != null && request.reason() != null ? request.reason().trim() : "Cancelled by tourist");
        bookingRepository.save(booking);

        executeSettlementRefunds(booking, preview, touristUserId, now);
        return dtoMapper.toDetail(booking);
    }

    @Transactional
    public BookingDtos.BookingDetailDto adminDisruptionOverride(String bookingPublicId,
                                                               BookingDtos.DisruptionOverrideRequest req,
                                                               Long adminUserId) {
        Booking booking = bookingRepository.findByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingPublicId));
        vehicleRepository.findByIdForUpdate(booking.getVehicle().getId());
        booking = bookingRepository.findByIdForUpdate(booking.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found."));

        Instant now = Instant.now();
        CancellationPolicySnapshot snapshot = cancellationSnapshotService.deserialize(booking.getCancellationPolicySnapshot());
        BigDecimal rentalPaid = booking.getAdvancePaidAmount() != null ? booking.getAdvancePaidAmount() : BigDecimal.ZERO;
        BigDecimal depositPaid = getCollectedDepositAmount(booking);

        var breakdown = cancellationSnapshotService.evaluateDisruptionOverride(
                snapshot,
                req.disruptionType(),
                rentalPaid,
                depositPaid,
                req.preferReschedule()
        );

        lifecycleService.transitionStatus(
                booking,
                BookingStatus.CANCELLED,
                BookingActorRole.ADMIN,
                adminUserId,
                "DISRUPTION_OVERRIDE: " + req.disruptionType() + " — " + req.reason(),
                now
        );
        booking.setCancelledAt(now);
        booking.setCancelledBy(adminUserId);
        booking.setCancelledByRole("ADMIN");
        booking.setDisruptionExceptionType(req.disruptionType());
        booking.setCancellationReason("Disruption override: " + req.reason());
        bookingRepository.save(booking);

        BookingDtos.CancellationPreviewResponse preview = new BookingDtos.CancellationPreviewResponse(
                booking.getPublicId(),
                booking.getStatus(),
                booking.getTotalPayableAmount(),
                rentalPaid,
                depositPaid,
                breakdown.rentalPenaltyAmount(),
                breakdown.gatewayFeeDeducted(),
                breakdown.netRentalRefundAmount(),
                breakdown.securityDepositRefundAmount(),
                breakdown.totalRefundAmount(),
                breakdown.depositProtected(),
                breakdown.reschedulingEligible(),
                breakdown.policyAppliedDescription(),
                now
        );
        executeSettlementRefunds(booking, preview, adminUserId, now);
        return dtoMapper.toDetail(booking);
    }

    @Transactional
    public BookingDtos.BookingDetailDto recordPickupHandover(Long staffUserId,
                                                             String bookingPublicId,
                                                             BookingDtos.PickupHandoverRequest req) {
        Booking booking = requireLockedBookingForPartnerWrite(bookingPublicId, staffUserId, false);
        tenantSecurityService.requireHandoverAccess(booking.getBusiness().getId());

        if (handoverRepository.findByBookingIdAndHandoverType(booking.getId(), HandoverType.PICKUP).isPresent()) {
            throw new ConflictException("Pickup handover has already been recorded for this booking.");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleException("Pickup handover can only be recorded for CONFIRMED bookings.");
        }

        Instant now = Instant.now();
        User staffUser = userRepository.findById(staffUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + staffUserId));

        // Record Offline Pickup Balance collection if applicable
        if (req.balanceCollectedAmount() != null && req.balanceCollectedAmount().compareTo(BigDecimal.ZERO) > 0) {
            tenantSecurityService.requireOfflineCollectionAccess(booking.getBusiness().getId());
            BigDecimal balanceAmount = req.balanceCollectedAmount().setScale(2, RoundingMode.HALF_UP);
            OfflineCollection collection = new OfflineCollection();
            collection.setBooking(booking);
            collection.setStaffUser(staffUser);
            collection.setAmount(balanceAmount);
            collection.setCollectionType("PICKUP_BALANCE");
            collection.setPaymentMethod(normalizePaymentMethod(req.balancePaymentMethod()));
            collection.setReceiptReference(req.balanceReceiptReference());
            collection.setNotes(req.notes());
            collection.setCollectedAt(now);
            offlineCollectionRepository.save(collection);

            Payment offlinePayment = new Payment();
            offlinePayment.setBooking(booking);
            offlinePayment.setAmount(balanceAmount);
            offlinePayment.setCurrency(booking.getCurrency());
            offlinePayment.setPaymentType(PaymentType.PICKUP_BALANCE);
            offlinePayment.setDeposit(false);
            PaymentMethod mappedOfflineMethod = "UPI_QR".equalsIgnoreCase(collection.getPaymentMethod())
                    ? PaymentMethod.OFFLINE_UPI
                    : PaymentMethod.OFFLINE_CASH;
            offlinePayment.setPaymentMethod(mappedOfflineMethod);
            offlinePayment.setStatus(PaymentStatus.CAPTURED);
            offlinePayment.setCapturedAt(now);
            offlinePayment.setIdempotencyKey("off_bal_" + booking.getPublicId() + "_" + UUID.randomUUID().toString().substring(0, 8));
            paymentRepository.save(offlinePayment);

            BigDecimal currentPickupDue = booking.getPickupBalanceDue() != null ? booking.getPickupBalanceDue() : BigDecimal.ZERO;
            booking.setPickupBalanceDue(currentPickupDue.subtract(balanceAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            booking.setPaymentSummaryStatus(PaymentSummaryStatus.PAID_IN_FULL);
        }

        // Record Security Deposit collection or waiver at pickup
        SecurityDepositRecord depRecord = depositRepository.findByBookingId(booking.getId())
                .orElseGet(() -> {
                    SecurityDepositRecord r = new SecurityDepositRecord();
                    r.setBooking(booking);
                    return r;
                });
        if (req.depositWaived()) {
            depRecord.setStatus(SecurityDepositStatus.WAIVED);
            depRecord.setNotes("Security deposit waived at pickup by staff: " + staffUser.getEmail());
            depositRepository.save(depRecord);
        } else if (req.depositCollectedAmount() != null && req.depositCollectedAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal depAmount = req.depositCollectedAmount().setScale(2, RoundingMode.HALF_UP);
            depRecord.setCollectedAmount(depAmount);
            depRecord.setStatus(SecurityDepositStatus.COLLECTED);
            depRecord.setCollectedBy(staffUserId);
            depRecord.setCollectedAt(now);
            depRecord.setPaymentChannel(req.depositPaymentMethod() != null ? req.depositPaymentMethod().toUpperCase(Locale.ROOT) : "CASH");
            depRecord.setReceiptReference(req.depositReceiptReference());
            depRecord.setNotes(req.notes());
            depositRepository.save(depRecord);

            OfflineCollection depOffline = new OfflineCollection();
            depOffline.setBooking(booking);
            depOffline.setStaffUser(staffUser);
            depOffline.setAmount(depAmount);
            depOffline.setCollectionType("SECURITY_DEPOSIT");
            depOffline.setPaymentMethod(normalizePaymentMethod(req.depositPaymentMethod()));
            depOffline.setReceiptReference(req.depositReceiptReference());
            depOffline.setNotes("Security deposit collected at physical hub");
            depOffline.setCollectedAt(now);
            offlineCollectionRepository.save(depOffline);
        }

        HandoverInspection inspection = new HandoverInspection();
        inspection.setBooking(booking);
        inspection.setHandoverType(HandoverType.PICKUP);
        inspection.setStaffUser(staffUser);
        inspection.setOdometerReading(req.odometerReading());
        inspection.setFuelPercentage(req.fuelPercentage());
        inspection.setChecklistData(req.checklistDataJson());
        inspection.setNotes(req.notes());
        inspection.setCompletedAt(now);

        if (req.evidence() != null) {
            for (BookingDtos.HandoverEvidenceInput ev : req.evidence()) {
                HandoverEvidence evidence = new HandoverEvidence();
                evidence.setInspection(inspection);
                evidence.setEvidenceType(parseEvidenceType(ev.evidenceType()));
                evidence.setImageS3Key(ev.imageS3Key());
                evidence.setMediaMimeType(ev.mediaMimeType() != null ? ev.mediaMimeType() : "image/jpeg");
                inspection.getEvidenceList().add(evidence);
            }
        }
        handoverRepository.save(inspection);

        lifecycleService.transitionStatus(
                booking,
                BookingStatus.ACTIVE,
                BookingActorRole.PARTNER,
                staffUserId,
                "Vehicle handed over to tourist",
                now
        );
        bookingRepository.save(booking);

        auditLogService.record(
                staffUserId,
                "HANDOVER_PICKUP_RECORDED",
                "HandoverInspection",
                inspection.getPublicId(),
                null,
                Map.of("bookingPublicId", booking.getPublicId(), "odometer", req.odometerReading())
        );
        return dtoMapper.toDetail(booking);
    }

    @Transactional
    public BookingDtos.BookingDetailDto recordReturnCompletion(Long staffUserId,
                                                               String bookingPublicId,
                                                               BookingDtos.ReturnCompleteRequest req) {
        Booking booking = requireLockedBookingForPartnerWrite(bookingPublicId, staffUserId, false);
        tenantSecurityService.requireHandoverAccess(booking.getBusiness().getId());

        if (booking.getStatus() != BookingStatus.ACTIVE) {
            throw new BusinessRuleException("Return inspection can only be completed on ACTIVE bookings.");
        }
        if (handoverRepository.findByBookingIdAndHandoverType(booking.getId(), HandoverType.RETURN).isPresent()) {
            throw new ConflictException("Return inspection has already been completed for this booking.");
        }
        HandoverInspection pickupInspection = handoverRepository.findByBookingIdAndHandoverType(booking.getId(), HandoverType.PICKUP)
                .orElse(null);
        if (pickupInspection != null && req.odometerReading() < pickupInspection.getOdometerReading()) {
            throw new BusinessRuleException("Return odometer reading (" + req.odometerReading()
                    + ") cannot be less than pickup odometer reading (" + pickupInspection.getOdometerReading() + ").");
        }

        Instant now = Instant.now();
        User staffUser = userRepository.findById(staffUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + staffUserId));

        HandoverInspection inspection = new HandoverInspection();
        inspection.setBooking(booking);
        inspection.setHandoverType(HandoverType.RETURN);
        inspection.setStaffUser(staffUser);
        inspection.setOdometerReading(req.odometerReading());
        inspection.setFuelPercentage(req.fuelPercentage());
        inspection.setChecklistData(req.checklistDataJson());
        inspection.setNotes(req.notes());
        inspection.setCompletedAt(now);

        if (req.evidence() != null) {
            for (BookingDtos.HandoverEvidenceInput ev : req.evidence()) {
                HandoverEvidence evidence = new HandoverEvidence();
                evidence.setInspection(inspection);
                evidence.setEvidenceType(parseEvidenceType(ev.evidenceType()));
                evidence.setImageS3Key(ev.imageS3Key());
                evidence.setMediaMimeType(ev.mediaMimeType() != null ? ev.mediaMimeType() : "image/jpeg");
                inspection.getEvidenceList().add(evidence);
            }
        }
        handoverRepository.save(inspection);

        SecurityDepositRecord depRecord = depositRepository.findByBookingId(booking.getId()).orElse(null);
        if (depRecord != null && depRecord.getStatus() == SecurityDepositStatus.COLLECTED) {
            EffectiveBookingPolicy policy = bookingPolicyService.resolvePolicy(booking.getVehicle());
            depRecord.setInspectionDeadlineAt(now.plus(Duration.ofHours(policy.depositInspectionWindowHours())));
            if (req.damageReported()) {
                depRecord.setInspectionStatus(DepositInspectionStatus.DAMAGE_CLAIMED);
                depRecord.setStatus(SecurityDepositStatus.INSPECTION_HOLD);
                depRecord.setNotes("Damage reported during return inspection: " + req.notes());
            } else if (req.releaseDepositImmediately()) {
                depRecord.setInspectionStatus(DepositInspectionStatus.PASSED);
                depRecord.setStatus(SecurityDepositStatus.RELEASED);
                depRecord.setRefundedAmount(depRecord.getCollectedAmount());
                depRecord.setReleasedBy(staffUserId);
                depRecord.setReleasedAt(now);
                depRecord.setNotes("Deposit returned in full upon vehicle return without incident.");
            } else {
                depRecord.setStatus(SecurityDepositStatus.INSPECTION_HOLD);
                depRecord.setInspectionStatus(DepositInspectionStatus.PENDING);
            }
            depositRepository.save(depRecord);
        }

        lifecycleService.transitionStatus(
                booking,
                BookingStatus.COMPLETED,
                BookingActorRole.PARTNER,
                staffUserId,
                "Vehicle returned and rental completed",
                now
        );
        booking.setCompletedAt(now);
        bookingRepository.save(booking);

        // Record official platform commission strictly excluding tax & deposit
        BigDecimal onlineRentalCollected = paymentRepository.findByBookingId(booking.getId()).stream()
                .filter(p -> !p.isDeposit() && p.getPaymentMethod() == PaymentMethod.GATEWAY)
                .filter(p -> p.getStatus() == PaymentStatus.CAPTURED || p.getStatus() == PaymentStatus.REFUNDED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal offlineRentalCollected = offlineCollectionRepository.findByBookingId(booking.getId()).stream()
                .filter(o -> "PICKUP_BALANCE".equalsIgnoreCase(o.getCollectionType()))
                .map(OfflineCollection::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        commissionCalculationService.calculateAndRecord(booking, onlineRentalCollected, offlineRentalCollected);

        auditLogService.record(
                staffUserId,
                "HANDOVER_RETURN_RECORDED",
                "HandoverInspection",
                inspection.getPublicId(),
                null,
                Map.of("bookingPublicId", booking.getPublicId(), "odometer", req.odometerReading())
        );
        return dtoMapper.toDetail(booking);
    }

    @Transactional
    public BookingDtos.BookingDetailDto markNoShow(Long staffUserId,
                                                   String bookingPublicId,
                                                   BookingDtos.NoShowRequest req) {
        Booking booking = requireLockedBookingForPartnerWrite(bookingPublicId, staffUserId, false);
        tenantSecurityService.requireNoShowAccess(booking.getBusiness().getId());

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleException("NO_SHOW can only be marked on CONFIRMED bookings.");
        }

        Instant now = Instant.now();
        CancellationPolicySnapshot snapshot = cancellationSnapshotService.deserialize(booking.getCancellationPolicySnapshot());
        BigDecimal rentalPaid = booking.getAdvancePaidAmount() != null ? booking.getAdvancePaidAmount() : BigDecimal.ZERO;
        BigDecimal depositPaid = getCollectedDepositAmount(booking);

        var breakdown = cancellationSnapshotService.evaluateNoShow(
                snapshot,
                booking.getStartTime(),
                now,
                req != null ? req.evidenceNotes() : null,
                req != null ? req.evidenceRef() : null,
                rentalPaid,
                depositPaid
        );

        lifecycleService.transitionStatus(
                booking,
                BookingStatus.NO_SHOW,
                BookingActorRole.PARTNER,
                staffUserId,
                "Tourist marked as NO_SHOW",
                now
        );
        booking.setNoShowAt(now);
        booking.setNoShowMarkedBy(staffUserId);
        booking.setNoShowEvidenceNotes(req != null ? req.evidenceNotes() : null);
        booking.setNoShowEvidenceRef(req != null ? req.evidenceRef() : null);
        bookingRepository.save(booking);

        // Pre-handover NO_SHOW waives/releases deposit in full
        waivePreHandoverDeposit(booking, "Pre-handover NO_SHOW");

        BookingDtos.CancellationPreviewResponse preview = new BookingDtos.CancellationPreviewResponse(
                booking.getPublicId(),
                booking.getStatus(),
                booking.getTotalPayableAmount(),
                rentalPaid,
                depositPaid,
                breakdown.rentalPenaltyAmount(),
                breakdown.gatewayFeeDeducted(),
                breakdown.netRentalRefundAmount(),
                breakdown.securityDepositRefundAmount(),
                breakdown.totalRefundAmount(),
                breakdown.depositProtected(),
                breakdown.reschedulingEligible(),
                breakdown.policyAppliedDescription(),
                now
        );
        executeSettlementRefunds(booking, preview, staffUserId, now);
        return dtoMapper.toDetail(booking);
    }

    @Transactional
    public BookingDtos.BookingDetailDto adminResolveDispute(String bookingPublicId,
                                                            BookingDtos.DisputeResolveRequest req,
                                                            Long adminUserId) {
        Booking booking = bookingRepository.findByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingPublicId));
        vehicleRepository.findByIdForUpdate(booking.getVehicle().getId());
        booking = bookingRepository.findByIdForUpdate(booking.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found."));

        Instant now = Instant.now();
        if (booking.getStatus() != BookingStatus.DISPUTED
                && booking.getStatus() != BookingStatus.CONFIRMED
                && booking.getStatus() != BookingStatus.ACTIVE) {
            throw new BusinessRuleException("Cannot resolve dispute on booking with status " + booking.getStatus());
        }

        if (req.targetStatus() != BookingStatus.COMPLETED && req.targetStatus() != BookingStatus.CANCELLED) {
            throw new BusinessRuleException("Dispute resolution target status must be COMPLETED or CANCELLED.");
        }

        lifecycleService.transitionStatus(
                booking,
                req.targetStatus(),
                BookingActorRole.ADMIN,
                adminUserId,
                "DISPUTE_RESOLVED: " + req.resolutionNotes(),
                now
        );
        bookingRepository.save(booking);

        if (req.refundAmount() != null && req.refundAmount().compareTo(BigDecimal.ZERO) > 0) {
            Payment capturedAdvance = paymentRepository.findByBookingIdOrderByCreatedAtDesc(booking.getId()).stream()
                    .filter(p -> p.getStatus() == PaymentStatus.CAPTURED && !p.isDeposit())
                    .findFirst()
                    .orElse(null);
            if (capturedAdvance != null) {
                refundOrchestrationService.initiateAndExecuteRefund(
                        booking,
                        capturedAdvance,
                        req.refundAmount(),
                        BigDecimal.ZERO,
                        RefundType.PARTIAL_ADJUSTMENT,
                        "Dispute settlement refund: " + req.resolutionNotes(),
                        adminUserId,
                        "disp_ref_" + booking.getPublicId() + "_" + UUID.randomUUID().toString().substring(0, 8),
                        now
                );
            }
        }
        return dtoMapper.toDetail(booking);
    }

    @Transactional
    public BookingDtos.BookingDetailDto settleSecurityDeposit(String bookingPublicId,
                                                             BookingDtos.DepositSettlementRequest req,
                                                             Long actorUserId) {
        Booking booking = bookingRepository.findByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingPublicId));
        tenantSecurityService.requireHandoverAccess(booking.getBusiness().getId());

        SecurityDepositRecord dep = depositRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Security deposit record not found for booking: " + bookingPublicId));

        BigDecimal release = req.releaseAmount() != null ? req.releaseAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal forfeit = req.forfeitAmount() != null ? req.forfeitAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        Instant now = Instant.now();

        if (release.add(forfeit).compareTo(dep.getCollectedAmount()) > 0) {
            throw new BusinessRuleException("Sum of release and forfeit amounts cannot exceed collected deposit " + dep.getCollectedAmount());
        }

        dep.setRefundedAmount(release);
        dep.setForfeitedAmount(forfeit);
        dep.setReleasedBy(actorUserId);
        dep.setReleasedAt(now);
        dep.setNotes(req.notes());
        if (forfeit.compareTo(BigDecimal.ZERO) > 0 && release.compareTo(BigDecimal.ZERO) > 0) {
            dep.setStatus(SecurityDepositStatus.PARTIALLY_RELEASED);
            dep.setInspectionStatus(DepositInspectionStatus.DAMAGE_CLAIMED);
        } else if (forfeit.compareTo(BigDecimal.ZERO) > 0) {
            dep.setStatus(SecurityDepositStatus.FORFEITED);
            dep.setInspectionStatus(DepositInspectionStatus.DAMAGE_CLAIMED);
        } else {
            dep.setStatus(SecurityDepositStatus.RELEASED);
            dep.setInspectionStatus(DepositInspectionStatus.PASSED);
        }
        depositRepository.save(dep);

        // If online deposit was collected via gateway and release > 0, trigger automated refund
        if (dep.getCollectionMethod() == SecurityDepositCollectionMethod.ONLINE && release.compareTo(BigDecimal.ZERO) > 0) {
            Payment onlineDepPayment = paymentRepository.findByBookingIdOrderByCreatedAtDesc(booking.getId()).stream()
                    .filter(Payment::isDeposit)
                    .filter(p -> p.getStatus() == PaymentStatus.CAPTURED)
                    .findFirst()
                    .orElse(null);
            if (onlineDepPayment != null) {
                refundOrchestrationService.initiateAndExecuteRefund(
                        booking,
                        onlineDepPayment,
                        release,
                        BigDecimal.ZERO,
                        RefundType.SECURITY_DEPOSIT,
                        "Security deposit release: " + req.notes(),
                        actorUserId,
                        "dep_rel_" + dep.getPublicId(),
                        now
                );
            }
        }
        return dtoMapper.toDetail(booking);
    }

    @Transactional
    public BookingDtos.OverbookingExceptionDto resolveOverbookingException(String exceptionPublicId,
                                                                          BookingDtos.ResolveOverbookingRequest req,
                                                                          Long adminUserId) {
        OverbookingExceptionRecord record = overbookingRepository.findByPublicId(exceptionPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Overbooking exception not found: " + exceptionPublicId));

        record.setStatus(OverbookingExceptionStatus.RESOLVED);
        record.setResolutionNotes(req.resolutionNotes().trim());
        record.setResolvedBy(adminUserId);
        record.setResolvedAt(Instant.now());
        record = overbookingRepository.save(record);

        Booking booking = record.getBooking();
        booking.setOverbookingExceptionStatus(OverbookingExceptionStatus.RESOLVED);
        bookingRepository.save(booking);

        auditLogService.record(
                adminUserId,
                "OVERBOOKING_EXCEPTION_RESOLVED",
                "OverbookingExceptionRecord",
                record.getPublicId(),
                null,
                Map.of("bookingPublicId", booking.getPublicId(), "notes", record.getResolutionNotes())
        );
        return BookingDtoMapper.toOverbookingDto(record);
    }

    @Transactional(readOnly = true)
    public Page<BookingDtos.BookingSummaryDto> listTouristBookings(Long touristUserId, Pageable pageable) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(touristUserId, pageable)
                .map(dtoMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public BookingDtos.BookingDetailDto getTouristBooking(Long touristUserId, String bookingPublicId) {
        Booking booking = bookingRepository.findByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingPublicId));
        if (!booking.getUser().getId().equals(touristUserId)) {
            throw new ResourceNotFoundException("Booking not found: " + bookingPublicId);
        }
        return dtoMapper.toDetail(booking);
    }

    @Transactional(readOnly = true)
    public Page<BookingDtos.BookingSummaryDto> listPartnerBookings(UserPrincipal principal,
                                                                   String optionalBusinessPublicId,
                                                                   Pageable pageable) {
        Collection<Long> businessIds;
        if (optionalBusinessPublicId != null && !optionalBusinessPublicId.isBlank()) {
            businessIds = List.of(tenantSecurityService.resolvePartnerBusinessForRead(optionalBusinessPublicId).getId());
        } else {
            businessIds = tenantSecurityService.getAuthorizedBusinessIds();
        }
        if (businessIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return bookingRepository.findByBusinessIdInOrderByCreatedAtDesc(businessIds, pageable)
                .map(dtoMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public BookingDtos.BookingDetailDto getPartnerBooking(UserPrincipal principal, String bookingPublicId) {
        Booking booking = bookingRepository.findByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingPublicId));
        tenantSecurityService.requireBusinessReadAccess(booking.getBusiness().getId());
        return dtoMapper.toDetail(booking);
    }

    @Transactional(readOnly = true)
    public Page<BookingDtos.BookingSummaryDto> listAdminBookings(Pageable pageable) {
        return bookingRepository.findAll(pageable).map(dtoMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public BookingDtos.BookingDetailDto getAdminBooking(String bookingPublicId) {
        Booking booking = bookingRepository.findByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingPublicId));
        return dtoMapper.toDetail(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.OverbookingExceptionDto> listOverbookingExceptions(OverbookingExceptionStatus status) {
        List<OverbookingExceptionRecord> records = status != null
                ? overbookingRepository.findByStatusOrderByDetectedAtDesc(status)
                : overbookingRepository.findAllByOrderByDetectedAtDesc();
        return records.stream().map(BookingDtoMapper::toOverbookingDto).toList();
    }

    @Transactional
    public int expireStaleBookings() {
        Instant now = Instant.now();
        int expiredCount = 0;

        List<Booking> staleApproval = bookingRepository.findByStatusAndApprovalExpiresAtBefore(
                BookingStatus.AWAITING_PARTNER_APPROVAL,
                now
        );
        for (Booking candidate : staleApproval) {
            vehicleRepository.findByIdForUpdate(candidate.getVehicle().getId());
            Booking locked = bookingRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (locked != null && locked.getStatus() == BookingStatus.AWAITING_PARTNER_APPROVAL) {
                lifecycleService.transitionStatus(
                        locked,
                        BookingStatus.EXPIRED,
                        BookingActorRole.SYSTEM,
                        null,
                        "Partner approval deadline elapsed",
                        now
                );
                waivePreHandoverDeposit(locked, "Approval deadline expired");
                expiredCount++;
            }
        }

        List<Booking> stalePayment = bookingRepository.findByStatusInAndPaymentDeadlineAtBefore(
                List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.APPROVED, BookingStatus.PAYMENT_FAILED),
                now
        );
        for (Booking candidate : stalePayment) {
            vehicleRepository.findByIdForUpdate(candidate.getVehicle().getId());
            Booking locked = bookingRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (locked != null && (locked.getStatus() == BookingStatus.PENDING_PAYMENT
                    || locked.getStatus() == BookingStatus.APPROVED
                    || locked.getStatus() == BookingStatus.PAYMENT_FAILED)) {
                lifecycleService.transitionStatus(
                        locked,
                        BookingStatus.EXPIRED,
                        BookingActorRole.SYSTEM,
                        null,
                        "Payment window deadline elapsed",
                        now
                );
                waivePreHandoverDeposit(locked, "Payment deadline expired");
                expiredCount++;
            }
        }
        return expiredCount;
    }

    private BookingDtos.CancellationPreviewResponse evaluateCancellationBreakdown(Booking booking, Instant now) {
        CancellationPolicySnapshot snapshot = cancellationSnapshotService.deserialize(booking.getCancellationPolicySnapshot());
        BigDecimal totalWithTax = booking.getTotalPayableAmount();
        BigDecimal rentalPaid = booking.getAdvancePaidAmount() != null ? booking.getAdvancePaidAmount() : BigDecimal.ZERO;
        BigDecimal depositPaid = getCollectedDepositAmount(booking);

        var breakdown = cancellationSnapshotService.evaluateCancellation(
                snapshot,
                booking.getStartTime(),
                now,
                totalWithTax,
                rentalPaid,
                depositPaid,
                BigDecimal.ZERO
        );

        return new BookingDtos.CancellationPreviewResponse(
                booking.getPublicId(),
                booking.getStatus(),
                totalWithTax,
                rentalPaid,
                depositPaid,
                breakdown.rentalPenaltyAmount(),
                breakdown.gatewayFeeDeducted(),
                breakdown.netRentalRefundAmount(),
                breakdown.securityDepositRefundAmount(),
                breakdown.totalRefundAmount(),
                breakdown.depositProtected(),
                breakdown.reschedulingEligible(),
                breakdown.policyAppliedDescription(),
                now
        );
    }

    private void executeSettlementRefunds(Booking booking,
                                         BookingDtos.CancellationPreviewResponse preview,
                                         Long actorUserId,
                                         Instant now) {
        BigDecimal rentalRefund = preview.netRentalRefundAmount();
        if (rentalRefund != null && rentalRefund.compareTo(BigDecimal.ZERO) > 0) {
            Payment capturedAdvance = paymentRepository.findByBookingIdOrderByCreatedAtDesc(booking.getId()).stream()
                    .filter(p -> p.getStatus() == PaymentStatus.CAPTURED && !p.isDeposit())
                    .findFirst()
                    .orElse(null);
            if (capturedAdvance != null) {
                refundOrchestrationService.initiateAndExecuteRefund(
                        booking,
                        capturedAdvance,
                        rentalRefund,
                        preview.gatewayFeeDeducted(),
                        RefundType.RENTAL,
                        preview.policyAppliedDescription(),
                        actorUserId,
                        "cxl_rent_" + booking.getPublicId() + "_" + UUID.randomUUID().toString().substring(0, 8),
                        now
                );
            }
        }

        BigDecimal depositRefund = preview.securityDepositRefundAmount();
        SecurityDepositRecord dep = depositRepository.findByBookingId(booking.getId()).orElse(null);
        if (dep != null) {
            if (dep.getStatus() == SecurityDepositStatus.PENDING) {
                dep.setStatus(SecurityDepositStatus.WAIVED);
                dep.setNotes("Cancelled prior to handover collection");
                depositRepository.save(dep);
            } else if (depositRefund != null && depositRefund.compareTo(BigDecimal.ZERO) > 0) {
                dep.setRefundedAmount(depositRefund);
                dep.setStatus(SecurityDepositStatus.RELEASED);
                dep.setReleasedAt(now);
                dep.setReleasedBy(actorUserId);
                depositRepository.save(dep);

                Payment capturedDeposit = paymentRepository.findByBookingIdOrderByCreatedAtDesc(booking.getId()).stream()
                        .filter(Payment::isDeposit)
                        .filter(p -> p.getStatus() == PaymentStatus.CAPTURED)
                        .findFirst()
                        .orElse(null);
                if (capturedDeposit != null) {
                    refundOrchestrationService.initiateAndExecuteRefund(
                            booking,
                            capturedDeposit,
                            depositRefund,
                            BigDecimal.ZERO,
                            RefundType.SECURITY_DEPOSIT,
                            "Security deposit return on cancellation",
                            actorUserId,
                            "cxl_dep_" + booking.getPublicId() + "_" + UUID.randomUUID().toString().substring(0, 8),
                            now
                    );
                }
            }
        }
    }

    private BigDecimal getCollectedDepositAmount(Booking booking) {
        return depositRepository.findByBookingId(booking.getId())
                .map(SecurityDepositRecord::getCollectedAmount)
                .orElse(BigDecimal.ZERO);
    }

    private void waivePreHandoverDeposit(Booking booking, String reason) {
        SecurityDepositRecord dep = depositRepository.findByBookingId(booking.getId()).orElse(null);
        if (dep != null && dep.getStatus() == SecurityDepositStatus.PENDING) {
            dep.setStatus(SecurityDepositStatus.WAIVED);
            dep.setNotes(reason);
            depositRepository.save(dep);
        }
    }

    private Booking requireLockedBookingForPartnerWrite(String bookingPublicId, Long partnerUserId, boolean requireApprovalPerm) {
        Booking lookup = bookingRepository.findByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingPublicId));
        if (requireApprovalPerm) {
            tenantSecurityService.requireBookingApprovalAccess(lookup.getBusiness().getId());
        } else {
            tenantSecurityService.requireBusinessReadAccess(lookup.getBusiness().getId());
        }
        vehicleRepository.findByIdForUpdate(lookup.getVehicle().getId());
        return bookingRepository.findByIdForUpdate(lookup.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingPublicId));
    }

    private void notifyPartnerForApproval(Booking booking) {
        partnerStaffRepository.findByBusinessIdAndActiveTrue(booking.getBusiness().getId()).stream()
                .map(s -> s.getUser().getId())
                .distinct()
                .limit(5)
                .forEach(partnerUserId -> notificationDispatchService.sendInAppNotification(
                        partnerUserId,
                        "New Booking Request: " + booking.getBookingReference(),
                        "A tourist requested booking for vehicle " + booking.getVehicle().getTitle() + ". Approve before " + booking.getApprovalExpiresAt(),
                        "/partner/bookings/" + booking.getPublicId()
                ));
    }

    private Location resolveLocation(String locationPublicId) {
        if (locationPublicId == null || locationPublicId.isBlank()) {
            return null;
        }
        return locationRepository.findByPublicId(locationPublicId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + locationPublicId));
    }

    private static String normalizePaymentMethod(String method) {
        if (method == null || method.isBlank()) {
            return "CASH";
        }
        String clean = method.trim().toUpperCase(Locale.ROOT);
        return clean.contains("UPI") ? "UPI_QR" : "CASH";
    }

    private static com.islandride.booking.domain.EvidenceType parseEvidenceType(String raw) {
        if (raw == null || raw.isBlank()) {
            return com.islandride.booking.domain.EvidenceType.SIGNATURE;
        }
        try {
            return com.islandride.booking.domain.EvidenceType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return com.islandride.booking.domain.EvidenceType.SIGNATURE;
        }
    }
}
