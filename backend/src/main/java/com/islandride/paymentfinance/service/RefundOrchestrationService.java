package com.islandride.paymentfinance.service;

import com.islandride.administration.service.AuditLogService;
import com.islandride.booking.domain.Booking;
import com.islandride.booking.domain.PaymentSummaryStatus;
import com.islandride.booking.repository.BookingRepository;
import com.islandride.booking.service.BookingDtoMapper;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.core.security.TenantSecurityService;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.notifications.service.NotificationDispatchService;
import com.islandride.paymentfinance.domain.Payment;
import com.islandride.paymentfinance.domain.PaymentStatus;
import com.islandride.paymentfinance.domain.Refund;
import com.islandride.paymentfinance.domain.RefundStatus;
import com.islandride.paymentfinance.domain.RefundType;
import com.islandride.paymentfinance.dto.PaymentDtos.RefundSummaryDto;
import com.islandride.paymentfinance.gateway.GatewayRefundRequest;
import com.islandride.paymentfinance.gateway.GatewayRefundResponse;
import com.islandride.paymentfinance.gateway.PaymentGatewayAdapter;
import com.islandride.paymentfinance.gateway.PaymentGatewayRegistry;
import com.islandride.paymentfinance.repository.PaymentRepository;
import com.islandride.paymentfinance.repository.RefundRepository;
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
public class RefundOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(RefundOrchestrationService.class);

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final RefundStateMachine refundStateMachine;
    private final PaymentStateMachine paymentStateMachine;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final BookingPolicyService bookingPolicyService;
    private final TenantSecurityService tenantSecurityService;
    private final NotificationDispatchService notificationDispatchService;
    private final AuditLogService auditLogService;

    public RefundOrchestrationService(RefundRepository refundRepository,
                                      PaymentRepository paymentRepository,
                                      BookingRepository bookingRepository,
                                      UserRepository userRepository,
                                      RefundStateMachine refundStateMachine,
                                      PaymentStateMachine paymentStateMachine,
                                      PaymentGatewayRegistry gatewayRegistry,
                                      BookingPolicyService bookingPolicyService,
                                      TenantSecurityService tenantSecurityService,
                                      NotificationDispatchService notificationDispatchService,
                                      AuditLogService auditLogService) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.refundStateMachine = refundStateMachine;
        this.paymentStateMachine = paymentStateMachine;
        this.gatewayRegistry = gatewayRegistry;
        this.bookingPolicyService = bookingPolicyService;
        this.tenantSecurityService = tenantSecurityService;
        this.notificationDispatchService = notificationDispatchService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Refund initiateAndExecuteRefund(Booking booking,
                                           Payment payment,
                                           BigDecimal amount,
                                           BigDecimal gatewayFeeRetained,
                                           RefundType refundType,
                                           String reason,
                                           Long initiatedByUserId,
                                           String idempotencyKey,
                                           Instant now) {
        if (booking == null || payment == null) {
            throw new BusinessRuleException("Booking and payment are required to initiate a refund.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Refund amount must be positive.");
        }
        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey.trim()
                : "ref_" + booking.getPublicId() + "_" + payment.getPublicId() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        Refund existing = refundRepository.findByIdempotencyKey(effectiveKey).orElse(null);
        if (existing != null) {
            return existing;
        }

        if (payment.getStatus() != PaymentStatus.CAPTURED && payment.getStatus() != PaymentStatus.REFUNDED) {
            throw new BusinessRuleException("Only captured gateway payments can be refunded cleanly.");
        }

        BigDecimal normalizedAmount = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal alreadyClaimed = refundRepository.findByPaymentId(payment.getId()).stream()
                .filter(r -> r.getStatus() != RefundStatus.FAILED || r.getRetryCount() < Math.max(1, bookingPolicyService.resolveRefundRetryMaxAttempts()))
                .filter(r -> r.getStatus() == RefundStatus.PENDING
                        || r.getStatus() == RefundStatus.PROCESSING
                        || r.getStatus() == RefundStatus.SUCCESS
                        || r.getStatus() == RefundStatus.RECONCILED)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remainingRefundable = payment.getAmount().subtract(alreadyClaimed).setScale(2, RoundingMode.HALF_UP);
        if (normalizedAmount.compareTo(remainingRefundable) > 0) {
            throw new BusinessRuleException(
                    "Requested refund " + normalizedAmount + " exceeds remaining refundable balance " + remainingRefundable
            );
        }

        Instant effectiveNow = now != null ? now : Instant.now();
        Refund refund = new Refund();
        refund.setBooking(booking);
        refund.setPayment(payment);
        refund.setAmount(normalizedAmount);
        refund.setGatewayFeeRetained(gatewayFeeRetained != null ? gatewayFeeRetained.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        refund.setCurrency(payment.getCurrency() != null ? payment.getCurrency() : "INR");
        refund.setRefundType(refundType != null ? refundType : RefundType.RENTAL);
        refund.setReason(reason != null && !reason.isBlank() ? reason.trim() : "CANCELLATION");
        refund.setStatus(RefundStatus.PENDING);
        refund.setInitiatedBy(initiatedByUserId);
        refund.setIdempotencyKey(effectiveKey);

        if (initiatedByUserId != null) {
            refund.setApprovedBy(initiatedByUserId);
            refund.setApprovedAt(effectiveNow);
        }

        refund = refundRepository.save(refund);
        attemptGatewayExecution(refund, payment, booking, effectiveNow);
        refreshBookingPaymentSummary(booking, payment);

        auditLogService.record(
                initiatedByUserId,
                "REFUND_INITIATED",
                "Refund",
                refund.getPublicId(),
                null,
                Map.of(
                        "bookingPublicId", booking.getPublicId(),
                        "paymentPublicId", payment.getPublicId(),
                        "amount", refund.getAmount().toPlainString(),
                        "refundType", refund.getRefundType().name(),
                        "status", refund.getStatus().name()
                )
        );
        return refund;
    }

    @Transactional
    public RefundSummaryDto createManualRefund(String bookingPublicId,
                                               BigDecimal amount,
                                               String reason,
                                               RefundType refundType,
                                               Long adminUserId,
                                               String idempotencyKey) {
        tenantSecurityService.requireFinancialRefundApprovalAccess();
        Booking booking = bookingRepository.findByPublicIdForUpdate(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingPublicId));

        Payment capturedGatewayPayment = paymentRepository.findByBookingIdOrderByCreatedAtDesc(booking.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.CAPTURED)
                .filter(p -> refundType == RefundType.SECURITY_DEPOSIT ? p.isDeposit() : !p.isDeposit())
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("No captured online payment eligible for manual refund on booking " + bookingPublicId));

        Refund refund = initiateAndExecuteRefund(
                booking,
                capturedGatewayPayment,
                amount,
                BigDecimal.ZERO,
                refundType != null ? refundType : RefundType.PARTIAL_ADJUSTMENT,
                reason,
                adminUserId,
                idempotencyKey,
                Instant.now()
        );
        return BookingDtoMapper.toRefundDto(refund);
    }

    @Transactional
    public RefundSummaryDto retryOrReconcileRefund(String refundPublicId, Long adminUserId, boolean forceMarkReconciled) {
        tenantSecurityService.requireFinancialRefundApprovalAccess();
        Refund refund = refundRepository.findByPublicIdForUpdate(refundPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundPublicId));
        Booking booking = bookingRepository.findByIdForUpdate(refund.getBooking().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found for refund: " + refundPublicId));
        Payment payment = refund.getPayment();
        Instant now = Instant.now();

        if (refund.getStatus() == RefundStatus.RECONCILED) {
            return BookingDtoMapper.toRefundDto(refund);
        }

        if (forceMarkReconciled && (refund.getStatus() == RefundStatus.SUCCESS || refund.getStatus() == RefundStatus.PROCESSING)) {
            refundStateMachine.transition(refund, RefundStatus.RECONCILED, null, now);
            refund.setApprovedBy(adminUserId);
            refund.setApprovedAt(now);
            refundRepository.save(refund);
            refreshBookingPaymentSummary(booking, payment);
            return BookingDtoMapper.toRefundDto(refund);
        }

        if (refund.getStatus() == RefundStatus.SUCCESS) {
            refundStateMachine.transition(refund, RefundStatus.RECONCILED, null, now);
            refund.setApprovedBy(adminUserId);
            refund.setApprovedAt(now);
            refundRepository.save(refund);
            refreshBookingPaymentSummary(booking, payment);
            return BookingDtoMapper.toRefundDto(refund);
        }

        if (refund.getStatus() == RefundStatus.PENDING || refund.getStatus() == RefundStatus.FAILED) {
            refund.setApprovedBy(adminUserId);
            refund.setApprovedAt(now);
            attemptGatewayExecution(refund, payment, booking, now);
            refreshBookingPaymentSummary(booking, payment);
        }

        auditLogService.record(
                adminUserId,
                "REFUND_MANUAL_RETRY",
                "Refund",
                refund.getPublicId(),
                null,
                Map.of("status", refund.getStatus().name(), "retryCount", refund.getRetryCount())
        );
        return BookingDtoMapper.toRefundDto(refund);
    }

    @Transactional
    public int retryDueRefunds() {
        Instant now = Instant.now();
        int maxRetries = Math.max(1, bookingPolicyService.resolveRefundRetryMaxAttempts());
        List<Refund> dueRefunds = refundRepository.findRetryEligibleRefunds(now, maxRetries);
        int processed = 0;

        for (Refund candidate : dueRefunds) {
            Refund locked = refundRepository.findByPublicIdForUpdate(candidate.getPublicId()).orElse(null);
            if (locked == null || (locked.getStatus() != RefundStatus.PENDING && locked.getStatus() != RefundStatus.FAILED)) {
                continue;
            }
            Booking booking = bookingRepository.findByIdForUpdate(locked.getBooking().getId()).orElse(null);
            if (booking == null) {
                continue;
            }
            attemptGatewayExecution(locked, locked.getPayment(), booking, now);
            refreshBookingPaymentSummary(booking, locked.getPayment());
            processed++;
        }
        return processed;
    }

    @Transactional
    public void handleRefundWebhook(String gatewayRefundId, boolean succeeded, String failureReason, Instant now) {
        if (gatewayRefundId == null || gatewayRefundId.isBlank()) {
            return;
        }
        Refund refund = refundRepository.findByGatewayRefundId(gatewayRefundId).orElse(null);
        if (refund == null) {
            return;
        }
        Booking booking = bookingRepository.findByIdForUpdate(refund.getBooking().getId()).orElse(null);
        if (booking == null) {
            return;
        }
        Instant effectiveNow = now != null ? now : Instant.now();
        if (succeeded) {
            if (refund.getStatus() == RefundStatus.PENDING || refund.getStatus() == RefundStatus.PROCESSING) {
                refundStateMachine.transition(refund, RefundStatus.SUCCESS, null, effectiveNow);
            }
            if (refund.getStatus() == RefundStatus.SUCCESS) {
                refundStateMachine.transition(refund, RefundStatus.RECONCILED, null, effectiveNow);
            }
        } else {
            if (refund.getStatus() == RefundStatus.PENDING || refund.getStatus() == RefundStatus.PROCESSING) {
                refundStateMachine.transition(refund, RefundStatus.FAILED, failureReason, effectiveNow);
                scheduleNextRetryOrEscalate(refund, booking, effectiveNow);
            }
        }
        refundRepository.save(refund);
        refreshBookingPaymentSummary(booking, refund.getPayment());
    }

    private void attemptGatewayExecution(Refund refund, Payment payment, Booking booking, Instant now) {
        if (refund.getStatus() == RefundStatus.PENDING || refund.getStatus() == RefundStatus.FAILED) {
            refundStateMachine.transition(refund, RefundStatus.PROCESSING, null, now);
        }
        try {
            PaymentGatewayAdapter adapter = gatewayRegistry.getAdapter(payment.getGatewayProvider());
            if (payment.getGatewayPaymentId() == null || payment.getGatewayPaymentId().isBlank()) {
                throw new IllegalStateException("Cannot execute gateway refund: payment is missing gatewayPaymentId.");
            }
            GatewayRefundResponse response = adapter.initiateRefund(new GatewayRefundRequest(
                    payment.getGatewayPaymentId(),
                    refund.getAmount(),
                    refund.getCurrency(),
                    refund.getReason(),
                    refund.getIdempotencyKey(),
                    Map.of("bookingPublicId", booking.getPublicId(), "paymentPublicId", payment.getPublicId())
            ));
            refund.setGatewayRefundId(response.gatewayRefundId());
            refundStateMachine.transition(refund, RefundStatus.SUCCESS, null, now);
            refundStateMachine.transition(refund, RefundStatus.RECONCILED, null, now);
            refundRepository.save(refund);

            if (booking.getUser() != null) {
                notificationDispatchService.sendInAppNotification(
                        booking.getUser().getId(),
                        "Refund Processed for " + booking.getBookingReference(),
                        "Refund of " + refund.getCurrency() + " " + refund.getAmount() + " has been initiated/reconciled.",
                        "/tourist/bookings/" + booking.getPublicId()
                );
            }
        } catch (RuntimeException ex) {
            log.warn("Refund execution attempt failed for refund {}: {}", refund.getPublicId(), ex.getMessage());
            refundStateMachine.transition(refund, RefundStatus.FAILED, ex.getMessage(), now);
            scheduleNextRetryOrEscalate(refund, booking, now);
            refundRepository.save(refund);
        }
    }

    private void scheduleNextRetryOrEscalate(Refund refund, Booking booking, Instant now) {
        int maxRetries = Math.max(1, bookingPolicyService.resolveRefundRetryMaxAttempts());
        int baseBackoffMinutes = Math.max(1, bookingPolicyService.resolveRefundRetryBackoffMinutes());
        if (refund.getRetryCount() < maxRetries) {
            long delayMinutes = (long) baseBackoffMinutes * Math.max(1, refund.getRetryCount());
            refund.setNextRetryAt(now.plus(Duration.ofMinutes(delayMinutes)));
        } else {
            refund.setNextRetryAt(null);
            if (refund.getEscalationTicketRef() == null || refund.getEscalationTicketRef().isBlank()) {
                refund.setEscalationTicketRef("ESC-" + refund.getPublicId().substring(Math.max(0, refund.getPublicId().length() - 8)).toUpperCase());
            }
            auditLogService.record(
                    null,
                    "REFUND_ESCALATED",
                    "Refund",
                    refund.getPublicId(),
                    null,
                    Map.of(
                            "bookingPublicId", booking.getPublicId(),
                            "retryCount", refund.getRetryCount(),
                            "escalationTicketRef", refund.getEscalationTicketRef()
                    )
            );
            notifyAdminsForEscalation(refund, booking);
        }
    }

    private void notifyAdminsForEscalation(Refund refund, Booking booking) {
        userRepository.findAll().stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> {
                    String n = r.getName().toUpperCase(java.util.Locale.ROOT);
                    return n.contains("FINANCE_ADMIN") || n.contains("OPERATIONS_ADMIN") || n.contains("SUPER_ADMIN");
                }))
                .limit(5)
                .forEach(admin -> notificationDispatchService.sendInAppNotification(
                        admin.getId(),
                        "Refund Retry Escalation: " + booking.getBookingReference(),
                        "Refund " + refund.getPublicId() + " exceeded max retries (" + refund.getRetryCount()
                                + "). Ticket: " + refund.getEscalationTicketRef(),
                        "/admin/finance"
                ));
    }

    public void refreshBookingPaymentSummary(Booking booking, Payment payment) {
        List<Refund> paymentRefunds = refundRepository.findByPaymentId(payment.getId());
        BigDecimal refundedOnPayment = paymentRefunds.stream()
                .filter(r -> r.getStatus() == RefundStatus.SUCCESS || r.getStatus() == RefundStatus.RECONCILED)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (payment.getStatus() == PaymentStatus.CAPTURED && refundedOnPayment.compareTo(payment.getAmount()) >= 0) {
            paymentStateMachine.transition(payment, PaymentStatus.REFUNDED, "Full payment refunded", Instant.now());
            paymentRepository.save(payment);
        }

        List<Refund> bookingRefunds = refundRepository.findByBookingId(booking.getId());
        boolean anyPending = bookingRefunds.stream()
                .anyMatch(r -> r.getStatus() == RefundStatus.PENDING
                        || r.getStatus() == RefundStatus.PROCESSING
                        || r.getStatus() == RefundStatus.FAILED);

        BigDecimal totalOnlineCaptured = paymentRepository.findByBookingId(booking.getId()).stream()
                .filter(p -> p.getPaymentMethod() != com.islandride.paymentfinance.domain.PaymentMethod.OFFLINE_CASH
                        && p.getPaymentMethod() != com.islandride.paymentfinance.domain.PaymentMethod.OFFLINE_UPI)
                .filter(p -> p.getStatus() == PaymentStatus.CAPTURED || p.getStatus() == PaymentStatus.REFUNDED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRefundedCompleted = bookingRefunds.stream()
                .filter(r -> r.getStatus() == RefundStatus.SUCCESS || r.getStatus() == RefundStatus.RECONCILED)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (anyPending) {
            booking.setPaymentSummaryStatus(PaymentSummaryStatus.REFUND_PENDING);
        } else if (totalOnlineCaptured.compareTo(BigDecimal.ZERO) > 0 && totalRefundedCompleted.compareTo(totalOnlineCaptured) >= 0) {
            booking.setPaymentSummaryStatus(PaymentSummaryStatus.REFUNDED);
        } else if (totalRefundedCompleted.compareTo(BigDecimal.ZERO) > 0) {
            booking.setPaymentSummaryStatus(PaymentSummaryStatus.PARTIALLY_REFUNDED);
        }
        bookingRepository.save(booking);
    }
}
