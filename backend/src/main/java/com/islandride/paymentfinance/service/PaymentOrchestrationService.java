package com.islandride.paymentfinance.service;

import com.islandride.administration.service.AuditLogService;
import com.islandride.availability.domain.InventoryHold;
import com.islandride.availability.repository.InventoryHoldRepository;
import com.islandride.booking.domain.Booking;
import com.islandride.booking.domain.BookingStatus;
import com.islandride.booking.domain.OverbookingExceptionRecord;
import com.islandride.booking.domain.OverbookingExceptionStatus;
import com.islandride.booking.domain.PaymentMode;
import com.islandride.booking.domain.PaymentSummaryStatus;
import com.islandride.booking.domain.SecurityDepositCollectionMethod;
import com.islandride.booking.domain.SecurityDepositRecord;
import com.islandride.booking.domain.SecurityDepositStatus;
import com.islandride.booking.repository.BookingRepository;
import com.islandride.booking.repository.OverbookingExceptionRecordRepository;
import com.islandride.booking.repository.SecurityDepositRecordRepository;
import com.islandride.booking.service.BookingActorRole;
import com.islandride.booking.service.BookingDtoMapper;
import com.islandride.booking.service.BookingLifecycleFoundationService;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.IdempotencyConflictException;
import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.inventory.domain.AvailabilityBlockStatus;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.domain.VehicleUnavailability;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.inventory.repository.VehicleUnavailabilityRepository;
import com.islandride.notifications.service.NotificationDispatchService;
import com.islandride.paymentfinance.domain.Payment;
import com.islandride.paymentfinance.domain.PaymentAttempt;
import com.islandride.paymentfinance.domain.PaymentMethod;
import com.islandride.paymentfinance.domain.PaymentStatus;
import com.islandride.paymentfinance.domain.PaymentType;
import com.islandride.paymentfinance.domain.RefundType;
import com.islandride.paymentfinance.dto.PaymentDtos.GatewayCheckoutOrderResponse;
import com.islandride.paymentfinance.dto.PaymentDtos.MockPaymentSimulateRequest;
import com.islandride.paymentfinance.dto.PaymentDtos.MockPaymentSimulateResponse;
import com.islandride.paymentfinance.dto.PaymentDtos.PaymentVerificationResponse;
import com.islandride.paymentfinance.dto.PaymentDtos.VerifyPaymentRequest;
import com.islandride.paymentfinance.gateway.CreateGatewayOrderRequest;
import com.islandride.paymentfinance.gateway.GatewayOrderResponse;
import com.islandride.paymentfinance.gateway.GatewayPaymentStatusResponse;
import com.islandride.paymentfinance.gateway.MockGatewayAdapter;
import com.islandride.paymentfinance.gateway.PaymentGatewayAdapter;
import com.islandride.paymentfinance.gateway.PaymentGatewayProperties;
import com.islandride.paymentfinance.gateway.PaymentGatewayRegistry;
import com.islandride.paymentfinance.gateway.RazorpaySignatureVerifier;
import com.islandride.paymentfinance.repository.PaymentAttemptRepository;
import com.islandride.paymentfinance.repository.PaymentRepository;
import com.islandride.pricing.service.BookingPolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrchestrationService.class);

    private static final List<String> NON_BLOCKING_BOOKING_STATUS_NAMES = BookingStatus.NON_BLOCKING_STATUSES.stream()
            .map(Enum::name)
            .toList();

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final InventoryHoldRepository holdRepository;
    private final VehicleUnavailabilityRepository unavailabilityRepository;
    private final SecurityDepositRecordRepository depositRepository;
    private final OverbookingExceptionRecordRepository overbookingRepository;
    private final UserRepository userRepository;
    private final PaymentStateMachine paymentStateMachine;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentGatewayProperties gatewayProperties;
    private final BookingLifecycleFoundationService lifecycleService;
    private final BookingPolicyService bookingPolicyService;
    private final RefundOrchestrationService refundOrchestrationService;
    private final NotificationDispatchService notificationDispatchService;
    private final AuditLogService auditLogService;

    public PaymentOrchestrationService(PaymentRepository paymentRepository,
                                       PaymentAttemptRepository attemptRepository,
                                       BookingRepository bookingRepository,
                                       VehicleRepository vehicleRepository,
                                       InventoryHoldRepository holdRepository,
                                       VehicleUnavailabilityRepository unavailabilityRepository,
                                       SecurityDepositRecordRepository depositRepository,
                                       OverbookingExceptionRecordRepository overbookingRepository,
                                       UserRepository userRepository,
                                       PaymentStateMachine paymentStateMachine,
                                       PaymentGatewayRegistry gatewayRegistry,
                                       PaymentGatewayProperties gatewayProperties,
                                       BookingLifecycleFoundationService lifecycleService,
                                       BookingPolicyService bookingPolicyService,
                                       RefundOrchestrationService refundOrchestrationService,
                                       NotificationDispatchService notificationDispatchService,
                                       AuditLogService auditLogService) {
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
        this.holdRepository = holdRepository;
        this.unavailabilityRepository = unavailabilityRepository;
        this.depositRepository = depositRepository;
        this.overbookingRepository = overbookingRepository;
        this.userRepository = userRepository;
        this.paymentStateMachine = paymentStateMachine;
        this.gatewayRegistry = gatewayRegistry;
        this.gatewayProperties = gatewayProperties;
        this.lifecycleService = lifecycleService;
        this.bookingPolicyService = bookingPolicyService;
        this.refundOrchestrationService = refundOrchestrationService;
        this.notificationDispatchService = notificationDispatchService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public GatewayCheckoutOrderResponse initiateOrRetryCheckoutOrder(String bookingPublicId,
                                                                     Long touristUserId,
                                                                     String idempotencyKey) {
        Booking lookup = bookingRepository.findByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingPublicId));
        if (!lookup.getUser().getId().equals(touristUserId)) {
            throw new ResourceNotFoundException("Booking not found: " + bookingPublicId);
        }

        // Canonical lock order: Vehicle -> Booking -> Payment
        vehicleRepository.findByIdForUpdate(lookup.getVehicle().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found for booking: " + bookingPublicId));
        Booking booking = bookingRepository.findByPublicIdForUpdate(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingPublicId));

        return createOrderOnLockedBooking(booking, touristUserId, idempotencyKey, Instant.now());
    }

    @Transactional
    public GatewayCheckoutOrderResponse createOrderOnLockedBooking(Booking booking,
                                                                   Long actorUserId,
                                                                   String idempotencyKey,
                                                                   Instant now) {
        Instant effectiveNow = now != null ? now : Instant.now();
        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey.trim()
                : "pay_" + booking.getPublicId() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        Payment existingByIdemp = paymentRepository.findByIdempotencyKey(effectiveKey).orElse(null);
        if (existingByIdemp != null) {
            if (!existingByIdemp.getBooking().getId().equals(booking.getId())) {
                throw new IdempotencyConflictException("Idempotency-Key was already used for a different booking.");
            }
            PaymentGatewayAdapter adapter = gatewayRegistry.getAdapter(existingByIdemp.getGatewayProvider());
            return toCheckoutOrderResponse(existingByIdemp, booking, adapter.isLiveEnabled());
        }

        if (booking.getStatus() == BookingStatus.APPROVED || booking.getStatus() == BookingStatus.PAYMENT_FAILED) {
            lifecycleService.transitionStatus(
                    booking,
                    BookingStatus.PENDING_PAYMENT,
                    BookingActorRole.TOURIST,
                    actorUserId,
                    "Initiated gateway payment order",
                    effectiveNow
            );
        } else if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            if (booking.getPaymentDeadlineAt() != null && !effectiveNow.isBefore(booking.getPaymentDeadlineAt())) {
                throw new BusinessRuleException("Payment window deadline has already expired for booking " + booking.getPublicId());
            }
        } else {
            throw new BusinessRuleException("Booking in status " + booking.getStatus() + " cannot accept online payment initiation.");
        }

        BigDecimal onlineDue = BookingDtoMapper.computeRemainingOnlineDue(booking);
        if (onlineDue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("No online payment balance is currently due for booking " + booking.getPublicId());
        }

        // Reuse active un-expired INITIATED gateway order if present for same amount to prevent orphaned duplicate orders
        List<Payment> existingPayments = paymentRepository.findByBookingIdOrderByCreatedAtDesc(booking.getId());
        for (Payment p : existingPayments) {
            if (p.getStatus() == PaymentStatus.INITIATED
                    && p.getPaymentMethod() == PaymentMethod.GATEWAY
                    && p.getAmount().compareTo(onlineDue) == 0
                    && (p.getGatewayOrderExpiresAt() == null || p.getGatewayOrderExpiresAt().isAfter(effectiveNow))) {
                PaymentGatewayAdapter adapter = gatewayRegistry.getAdapter(p.getGatewayProvider());
                return toCheckoutOrderResponse(p, booking, adapter.isLiveEnabled());
            }
        }

        PaymentType paymentType = booking.getPaymentMode() == PaymentMode.PARTIAL_ADVANCE
                ? PaymentType.RENTAL_ADVANCE
                : PaymentType.RENTAL_FULL;

        PaymentGatewayAdapter adapter = gatewayRegistry.getConfiguredAdapter();
        GatewayOrderResponse orderResponse = adapter.createOrder(new CreateGatewayOrderRequest(
                booking.getBookingReference(),
                onlineDue,
                booking.getCurrency(),
                effectiveKey,
                Map.of("bookingPublicId", booking.getPublicId(), "paymentType", paymentType.name())
        ));

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(onlineDue);
        payment.setCurrency(booking.getCurrency());
        payment.setPaymentType(paymentType);
        payment.setDeposit(false);
        payment.setPaymentMethod(PaymentMethod.GATEWAY);
        payment.setGatewayProvider(adapter.providerCode());
        payment.setGatewayOrderId(orderResponse.gatewayOrderId());
        payment.setGatewayOrderExpiresAt(orderResponse.expiresAt());
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setIdempotencyKey(effectiveKey);
        payment = paymentRepository.save(payment);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPayment(payment);
        attempt.setAttemptNumber(1);
        attempt.setGatewayRequestPayload("{\"bookingPublicId\":\"" + booking.getPublicId() + "\",\"amount\":" + onlineDue + "}");
        attempt.setGatewayResponsePayload(orderResponse.rawPayload());
        attempt.setStatus("ORDER_CREATED");
        attemptRepository.save(attempt);

        booking.setPaymentSummaryStatus(PaymentSummaryStatus.ORDER_CREATED);
        bookingRepository.save(booking);

        return toCheckoutOrderResponse(payment, booking, adapter.isLiveEnabled());
    }

    @Transactional
    public PaymentVerificationResponse verifyClientPayment(Long touristUserId,
                                                         VerifyPaymentRequest req,
                                                         String idempotencyKey) {
        Booking lookup = bookingRepository.findByPublicId(req.bookingPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + req.bookingPublicId()));
        if (!lookup.getUser().getId().equals(touristUserId)) {
            throw new ResourceNotFoundException("Booking not found: " + req.bookingPublicId());
        }

        Payment paymentLookup = paymentRepository.findByGatewayOrderId(req.gatewayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found: " + req.gatewayOrderId()));
        if (!paymentLookup.getBooking().getId().equals(lookup.getId())) {
            throw new BusinessRuleException("Gateway order does not belong to booking " + req.bookingPublicId());
        }

        PaymentGatewayAdapter adapter = gatewayRegistry.getAdapter(paymentLookup.getGatewayProvider());
        boolean signatureValid = adapter.verifyCheckoutSignature(
                req.gatewayOrderId(),
                req.gatewayPaymentId(),
                req.gatewaySignature()
        );
        if (!signatureValid) {
            recordAttempt(paymentLookup, "SIGNATURE_FAILED", "INVALID_SIGNATURE", "Client checkout signature verification failed.");
            throw new BusinessRuleException("Invalid payment gateway checkout signature.");
        }

        // Canonical lock order: Vehicle -> Booking -> Payment
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(lookup.getVehicle().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found."));
        Booking booking = bookingRepository.findByIdForUpdate(lookup.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found."));
        Payment payment = paymentRepository.findByPublicIdForUpdate(paymentLookup.getPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found."));

        Instant now = Instant.now();
        // Interactive client verify refuses capture after payment deadline (client must rely on webhook reconciliation)
        if (booking.getStatus() == BookingStatus.EXPIRED
                || (booking.getPaymentDeadlineAt() != null && !now.isBefore(booking.getPaymentDeadlineAt()))) {
            throw new BusinessRuleException("Payment window deadline has already expired for booking " + booking.getPublicId());
        }

        boolean overbooked = capturePaymentLocked(
                vehicle,
                booking,
                payment,
                req.gatewayPaymentId().trim(),
                req.gatewaySignature().trim(),
                "Client checkout signature verified",
                now,
                touristUserId
        );

        return new PaymentVerificationResponse(
                booking.getPublicId(),
                booking.getBookingReference(),
                booking.getStatus().name(),
                booking.getPaymentSummaryStatus().name(),
                BookingDtoMapper.toPaymentDto(payment),
                overbooked
        );
    }

    @Transactional
    public void processWebhookCapture(String providerCode,
                                      String gatewayOrderId,
                                      String gatewayPaymentId,
                                      Instant now) {
        Payment paymentLookup = resolvePaymentForWebhook(gatewayOrderId, gatewayPaymentId);
        if (paymentLookup == null) {
            log.warn("Ignoring webhook capture for unknown order {} / payment {}", gatewayOrderId, gatewayPaymentId);
            return;
        }
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(paymentLookup.getBooking().getVehicle().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found."));
        Booking booking = bookingRepository.findByIdForUpdate(paymentLookup.getBooking().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found."));
        Payment payment = paymentRepository.findByPublicIdForUpdate(paymentLookup.getPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found."));

        capturePaymentLocked(
                vehicle,
                booking,
                payment,
                gatewayPaymentId != null ? gatewayPaymentId : ("whk_pay_" + payment.getPublicId()),
                payment.getGatewaySignature(),
                "Verified " + providerCode + " payment.captured webhook",
                now != null ? now : Instant.now(),
                null
        );
    }

    @Transactional
    public void processWebhookFailure(String providerCode,
                                      String gatewayOrderId,
                                      String gatewayPaymentId,
                                      String failureReason,
                                      Instant now) {
        Payment paymentLookup = resolvePaymentForWebhook(gatewayOrderId, gatewayPaymentId);
        if (paymentLookup == null) {
            return;
        }
        vehicleRepository.findByIdForUpdate(paymentLookup.getBooking().getVehicle().getId());
        Booking booking = bookingRepository.findByIdForUpdate(paymentLookup.getBooking().getId()).orElse(null);
        Payment payment = paymentRepository.findByPublicIdForUpdate(paymentLookup.getPublicId()).orElse(null);
        if (booking == null || payment == null) {
            return;
        }

        Instant effectiveNow = now != null ? now : Instant.now();
        if (payment.getStatus() == PaymentStatus.INITIATED) {
            payment.setGatewayPaymentId(gatewayPaymentId);
            paymentStateMachine.transition(payment, PaymentStatus.FAILED, failureReason, effectiveNow);
            paymentRepository.save(payment);
            recordAttempt(payment, "FAILED", "GATEWAY_PAYMENT_FAILED", failureReason);
        }

        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT || booking.getStatus() == BookingStatus.APPROVED) {
            booking.setPaymentSummaryStatus(PaymentSummaryStatus.FAILED);
            boolean withinDeadline = booking.getPaymentDeadlineAt() == null || effectiveNow.isBefore(booking.getPaymentDeadlineAt());
            BookingStatus next = withinDeadline ? BookingStatus.PAYMENT_FAILED : BookingStatus.EXPIRED;
            lifecycleService.transitionStatus(
                    booking,
                    next,
                    BookingActorRole.SYSTEM,
                    null,
                    withinDeadline ? ("Gateway payment failed: " + failureReason) : "Payment failed and deadline expired",
                    effectiveNow
            );
            if (booking.getUser() != null) {
                notificationDispatchService.sendInAppNotification(
                        booking.getUser().getId(),
                        withinDeadline ? "Payment Failed — Action Needed" : "Booking Expired",
                        withinDeadline
                                ? "Your payment failed for " + booking.getBookingReference() + ". Retry before deadline."
                                : "Booking " + booking.getBookingReference() + " expired after payment failure.",
                        "/tourist/bookings/" + booking.getPublicId()
                );
            }
        }
    }

    @Transactional
    public int reconcileStaleInitiatedPayments() {
        int staleMinutes = Math.max(1, bookingPolicyService.resolvePaymentReconciliationStaleMinutes());
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofMinutes(staleMinutes));
        List<Payment> stalePayments = paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.INITIATED, cutoff);
        int reconciledCount = 0;

        for (Payment candidate : stalePayments) {
            if (candidate.getGatewayOrderId() == null) {
                continue;
            }
            PaymentGatewayAdapter adapter = gatewayRegistry.getAdapter(candidate.getGatewayProvider());
            if (!adapter.isLiveEnabled() && !MockGatewayAdapter.PROVIDER_CODE.equalsIgnoreCase(adapter.providerCode())) {
                continue;
            }
            try {
                String lookupKey = candidate.getGatewayPaymentId() != null ? candidate.getGatewayPaymentId() : candidate.getGatewayOrderId();
                GatewayPaymentStatusResponse statusRes = adapter.fetchPaymentStatus(lookupKey);
                if (statusRes != null && "captured".equalsIgnoreCase(statusRes.status())) {
                    processWebhookCapture(adapter.providerCode(), candidate.getGatewayOrderId(), statusRes.gatewayPaymentId(), now);
                    reconciledCount++;
                } else if (statusRes != null && "failed".equalsIgnoreCase(statusRes.status())) {
                    processWebhookFailure(adapter.providerCode(), candidate.getGatewayOrderId(), statusRes.gatewayPaymentId(), "Gateway reconciliation reported failure", now);
                    reconciledCount++;
                }
            } catch (RuntimeException ex) {
                log.debug("Payment reconciliation skipped/failed for {}: {}", candidate.getPublicId(), ex.getMessage());
            }
        }
        return reconciledCount;
    }

    @Transactional(readOnly = true)
    public MockPaymentSimulateResponse buildMockSimulationPayload(MockPaymentSimulateRequest request) {
        if (gatewayProperties.isLiveEnabled() || !MockGatewayAdapter.PROVIDER_CODE.equalsIgnoreCase(gatewayProperties.getProvider())) {
            throw new BusinessRuleException("Mock payment simulator is only available when mock gateway mode is active and live mode is off.");
        }
        Payment payment = paymentRepository.findByGatewayOrderId(request.gatewayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + request.gatewayOrderId()));
        String gatewayPaymentId = "pay_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String checkoutSig = RazorpaySignatureVerifier.computeHmacHex(
                request.gatewayOrderId() + "|" + gatewayPaymentId,
                gatewayProperties.getMock().getKeySecret()
        );
        String eventType = "PAYMENT_FAILED".equalsIgnoreCase(request.outcome()) ? "payment.failed" : "payment.captured";
        String webhookEventId = "evt_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String reason = request.failureReason() != null && !request.failureReason().isBlank()
                ? request.failureReason().replace("\"", "'")
                : "Simulated failure";
        String webhookPayload = """
                {"id":"%s","event":"%s","payload":{"payment":{"entity":{"id":"%s","order_id":"%s","status":"%s","amount":%s,"error_description":"%s"}}}}
                """.formatted(
                webhookEventId,
                eventType,
                gatewayPaymentId,
                request.gatewayOrderId(),
                "payment.failed".equals(eventType) ? "failed" : "captured",
                payment.getAmount().movePointRight(2).longValue(),
                reason
        ).trim();
        String webhookSig = RazorpaySignatureVerifier.computeHmacHex(
                webhookPayload,
                gatewayProperties.getMock().getWebhookSecret()
        );

        return new MockPaymentSimulateResponse(
                request.gatewayOrderId(),
                gatewayPaymentId,
                checkoutSig,
                webhookEventId,
                webhookPayload,
                webhookSig,
                eventType
        );
    }

    private boolean capturePaymentLocked(Vehicle vehicle,
                                         Booking booking,
                                         Payment payment,
                                         String gatewayPaymentId,
                                         String gatewaySignature,
                                         String captureSourceNote,
                                         Instant now,
                                         Long actorUserId) {
        if (payment.getStatus() == PaymentStatus.CAPTURED || payment.getStatus() == PaymentStatus.REFUNDED) {
            return booking.getOverbookingExceptionStatus() != OverbookingExceptionStatus.NONE;
        }

        payment.setGatewayPaymentId(gatewayPaymentId);
        payment.setGatewaySignature(gatewaySignature);
        paymentStateMachine.transition(payment, PaymentStatus.CAPTURED, captureSourceNote, now);
        paymentRepository.save(payment);
        recordAttempt(payment, "CAPTURED", null, captureSourceNote);

        // If booking is ALREADY confirmed / active / completed (duplicate payment on concurrent retry), auto-refund duplicate capture
        if (booking.getStatus() == BookingStatus.CONFIRMED
                || booking.getStatus() == BookingStatus.ACTIVE
                || booking.getStatus() == BookingStatus.COMPLETED) {
            refundOrchestrationService.initiateAndExecuteRefund(
                    booking,
                    payment,
                    payment.getAmount(),
                    BigDecimal.ZERO,
                    RefundType.PARTIAL_ADJUSTMENT,
                    "Duplicate online payment capture auto-refunded",
                    actorUserId,
                    "dup_ref_" + payment.getPublicId(),
                    now
            );
            return false;
        }

        // Standard deadline-valid transition
        if ((booking.getStatus() == BookingStatus.PENDING_PAYMENT
                || booking.getStatus() == BookingStatus.PAYMENT_FAILED
                || booking.getStatus() == BookingStatus.APPROVED)
                && (booking.getPaymentDeadlineAt() == null || now.isBefore(booking.getPaymentDeadlineAt()))) {
            applyRentalCaptureAmounts(booking, payment);
            lifecycleService.transitionStatus(
                    booking,
                    BookingStatus.CONFIRMED,
                    BookingActorRole.SYSTEM,
                    null,
                    captureSourceNote,
                    now
            );
            notifyConfirmation(booking, payment);
            auditLogService.record(
                    actorUserId,
                    "PAYMENT_CAPTURED",
                    "Booking",
                    booking.getPublicId(),
                    null,
                    Map.of("paymentPublicId", payment.getPublicId(), "amount", payment.getAmount().toPlainString())
            );
            return false;
        }

        // Past deadline while still PENDING/APPROVED/FAILED -> expire atomically first, then arbitrate slot!
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT
                || booking.getStatus() == BookingStatus.PAYMENT_FAILED
                || booking.getStatus() == BookingStatus.APPROVED) {
            lifecycleService.transitionStatus(
                    booking,
                    BookingStatus.EXPIRED,
                    BookingActorRole.SYSTEM,
                    null,
                    "Payment deadline elapsed prior to late gateway capture",
                    now
            );
        }

        if (booking.getStatus() == BookingStatus.EXPIRED) {
            int bufferMinutes = booking.getTurnaroundBufferMinutes();
            List<Booking> conflictingBookings = bookingRepository.findOverlappingBookingsWithBufferExcludingBooking(
                    vehicle.getId(),
                    booking.getId(),
                    booking.getStartTime(),
                    booking.getEndTime(),
                    bufferMinutes,
                    NON_BLOCKING_BOOKING_STATUS_NAMES
            );
            List<InventoryHold> conflictingHolds = holdRepository.findActiveOverlappingHoldsWithBuffer(
                    vehicle.getId(),
                    booking.getStartTime(),
                    booking.getEndTime(),
                    bufferMinutes,
                    now
            );
            List<VehicleUnavailability> blocks = unavailabilityRepository.findOverlappingBlocks(
                    vehicle.getId(),
                    booking.getStartTime(),
                    booking.getEndTime(),
                    AvailabilityBlockStatus.ACTIVE
            );

            if (conflictingBookings.isEmpty() && conflictingHolds.isEmpty() && blocks.isEmpty()) {
                applyRentalCaptureAmounts(booking, payment);
                lifecycleService.transitionStatus(
                        booking,
                        BookingStatus.CONFIRMED,
                        BookingActorRole.SYSTEM,
                        null,
                        "Late webhook capture succeeded while vehicle slot remained free",
                        now
                );
                notifyConfirmation(booking, payment);
                return false;
            } else {
                Booking winner = conflictingBookings.isEmpty() ? null : conflictingBookings.get(0);
                lifecycleService.transitionStatus(
                        booking,
                        BookingStatus.CANCELLED,
                        BookingActorRole.SYSTEM,
                        null,
                        "OVERBOOKED_EXCEPTION: late capture detected after slot was claimed",
                        now
                );
                booking.setCancelledAt(now);
                booking.setCancelledByRole("SYSTEM");
                booking.setCancellationReason("Late payment arrived after expiry and vehicle slot was already booked.");
                recordOverbookingAndAutoRefund(booking, payment, winner, now);
                return true;
            }
        }

        // Terminal CANCELLED / REJECTED / NO_SHOW / DISPUTED receiving late payment -> OVERBOOKED_EXCEPTION + auto-refund
        recordOverbookingAndAutoRefund(booking, payment, null, now);
        return true;
    }

    private void recordOverbookingAndAutoRefund(Booking booking,
                                                Payment payment,
                                                Booking conflictingBooking,
                                                Instant now) {
        booking.setOverbookingExceptionStatus(OverbookingExceptionStatus.DETECTED);
        bookingRepository.save(booking);

        OverbookingExceptionRecord exceptionRecord = overbookingRepository.findByBookingId(booking.getId())
                .orElseGet(OverbookingExceptionRecord::new);
        exceptionRecord.setBooking(booking);
        exceptionRecord.setPaymentId(payment.getId());
        exceptionRecord.setConflictingBooking(conflictingBooking);
        exceptionRecord.setStatus(OverbookingExceptionStatus.DETECTED);
        exceptionRecord.setResolutionNotes("Late capture detected; triggering automatic 100% refund.");
        exceptionRecord = overbookingRepository.save(exceptionRecord);

        waivePreHandoverDepositIfPending(booking);

        refundOrchestrationService.initiateAndExecuteRefund(
                booking,
                payment,
                payment.getAmount(),
                BigDecimal.ZERO,
                RefundType.OVERBOOKING_AUTO_REFUND,
                "OVERBOOKING_AUTO_REFUND",
                null,
                "obx_ref_" + payment.getPublicId(),
                now
        );

        exceptionRecord.setStatus(OverbookingExceptionStatus.REFUND_INITIATED);
        overbookingRepository.save(exceptionRecord);
        booking.setOverbookingExceptionStatus(OverbookingExceptionStatus.REFUND_INITIATED);
        bookingRepository.save(booking);

        if (booking.getUser() != null) {
            notificationDispatchService.sendInAppNotification(
                    booking.getUser().getId(),
                    "Booking Unavailable — Full Refund Initiated (" + booking.getBookingReference() + ")",
                    "Your late payment succeeded after the vehicle slot expired and was booked by someone else. A 100% automatic refund has been initiated.",
                    "/tourist/bookings/" + booking.getPublicId()
            );
        }
        userRepository.findAll().stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> {
                    String n = r.getName().toUpperCase(java.util.Locale.ROOT);
                    return n.contains("OPERATIONS_ADMIN") || n.contains("FINANCE_ADMIN") || n.contains("SUPER_ADMIN");
                }))
                .limit(5)
                .forEach(admin -> notificationDispatchService.sendInAppNotification(
                        admin.getId(),
                        "OVERBOOKED_EXCEPTION Detected: " + booking.getBookingReference(),
                        "Late payment capture conflicted on vehicle " + booking.getVehicle().getPublicId() + ". Auto-refund triggered.",
                        "/admin/bookings"
                ));

        auditLogService.record(
                null,
                "OVERBOOKING_EXCEPTION_DETECTED",
                "OverbookingExceptionRecord",
                exceptionRecord.getPublicId(),
                null,
                Map.of(
                        "bookingPublicId", booking.getPublicId(),
                        "paymentPublicId", payment.getPublicId(),
                        "conflictingBookingPublicId", conflictingBooking != null ? conflictingBooking.getPublicId() : "NONE"
                )
        );
    }

    private void waivePreHandoverDepositIfPending(Booking booking) {
        SecurityDepositRecord dep = depositRepository.findByBookingId(booking.getId()).orElse(null);
        if (dep != null && dep.getStatus() == SecurityDepositStatus.PENDING
                && dep.getCollectionMethod() == SecurityDepositCollectionMethod.AT_PICKUP) {
            dep.setStatus(SecurityDepositStatus.WAIVED);
            dep.setNotes("Booking cancelled due to overbooking exception prior to handover.");
            depositRepository.save(dep);
        }
    }

    private void applyRentalCaptureAmounts(Booking booking, Payment payment) {
        if (!payment.isDeposit()) {
            BigDecimal currentPaid = booking.getAdvancePaidAmount() != null ? booking.getAdvancePaidAmount() : BigDecimal.ZERO;
            BigDecimal newPaid = currentPaid.add(payment.getAmount()).setScale(2, RoundingMode.HALF_UP);
            booking.setAdvancePaidAmount(newPaid);
            BigDecimal remainingOnline = BookingDtoMapper.computeRemainingOnlineDue(booking);
            BigDecimal pickupDue = booking.getPickupBalanceDue() != null ? booking.getPickupBalanceDue() : BigDecimal.ZERO;
            if (remainingOnline.compareTo(BigDecimal.ZERO) == 0 && pickupDue.compareTo(BigDecimal.ZERO) == 0) {
                booking.setPaymentSummaryStatus(PaymentSummaryStatus.PAID_IN_FULL);
            } else {
                booking.setPaymentSummaryStatus(PaymentSummaryStatus.PARTIALLY_PAID);
            }
        }
    }

    private void notifyConfirmation(Booking booking, Payment payment) {
        if (booking.getUser() != null) {
            notificationDispatchService.sendInAppNotification(
                    booking.getUser().getId(),
                    "Booking Confirmed: " + booking.getBookingReference(),
                    "Payment of " + payment.getCurrency() + " " + payment.getAmount() + " captured. Your ride is confirmed!",
                    "/tourist/bookings/" + booking.getPublicId()
            );
        }
    }

    private Payment resolvePaymentForWebhook(String gatewayOrderId, String gatewayPaymentId) {
        if (gatewayOrderId != null && !gatewayOrderId.isBlank()) {
            Payment byOrder = paymentRepository.findByGatewayOrderId(gatewayOrderId.trim()).orElse(null);
            if (byOrder != null) {
                return byOrder;
            }
        }
        if (gatewayPaymentId != null && !gatewayPaymentId.isBlank()) {
            return paymentRepository.findByGatewayPaymentId(gatewayPaymentId.trim()).orElse(null);
        }
        return null;
    }

    private void recordAttempt(Payment payment, String status, String errorCode, String message) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPayment(payment);
        attempt.setAttemptNumber(attemptRepository.findByPaymentIdOrderByAttemptNumberAsc(payment.getId()).size() + 1);
        attempt.setStatus(status);
        attempt.setErrorCode(errorCode);
        attempt.setErrorMessage(message);
        attemptRepository.save(attempt);
    }

    private static GatewayCheckoutOrderResponse toCheckoutOrderResponse(Payment p, Booking b, boolean liveEnabled) {
        return new GatewayCheckoutOrderResponse(
                p.getPublicId(),
                b.getPublicId(),
                b.getBookingReference(),
                p.getGatewayProvider(),
                p.getGatewayOrderId(),
                p.getAmount(),
                p.getCurrency(),
                p.getPaymentType(),
                liveEnabled,
                p.getGatewayOrderExpiresAt(),
                b.getPaymentDeadlineAt()
        );
    }
}
