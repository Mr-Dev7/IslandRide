package com.islandride.booking.service;

import com.islandride.administration.service.AuditLogService;
import com.islandride.booking.domain.*;
import com.islandride.booking.repository.BookingRepository;
import com.islandride.booking.repository.BookingStatusHistoryRepository;
import com.islandride.core.idempotency.IdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class BookingLifecycleFoundationService {

    private final BookingStateMachine bookingStateMachine;
    private final BookingRepository bookingRepository;
    private final BookingStatusHistoryRepository statusHistoryRepository;
    private final AuditLogService auditLogService;
    private final IdempotencyService idempotencyService;

    public BookingLifecycleFoundationService(BookingStateMachine bookingStateMachine,
                                             BookingRepository bookingRepository,
                                             BookingStatusHistoryRepository statusHistoryRepository,
                                             AuditLogService auditLogService,
                                             IdempotencyService idempotencyService) {
        this.bookingStateMachine = bookingStateMachine;
        this.bookingRepository = bookingRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.auditLogService = auditLogService;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public Booking transitionStatus(Booking booking,
                                    BookingStatus targetStatus,
                                    BookingActorRole actorRole,
                                    Long actorUserId,
                                    String reason,
                                    Instant now) {
        Instant effectiveNow = now != null ? now : Instant.now();
        BookingStatus previousStatus = booking.getStatus();
        bookingStateMachine.validateTransition(booking, targetStatus, actorRole, effectiveNow);

        booking.setStatus(targetStatus);
        applyLifecycleTimestamps(booking, targetStatus, actorRole, actorUserId, reason, effectiveNow);

        Booking saved = bookingRepository.save(booking);

        BookingStatusHistory history = new BookingStatusHistory();
        history.setBooking(saved);
        history.setFromStatus(previousStatus.name());
        history.setToStatus(targetStatus.name());
        history.setChangeReason(reason);
        history.setChangedBy(actorUserId);
        statusHistoryRepository.save(history);

        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("status", previousStatus.name());
        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("status", targetStatus.name());
        if (reason != null && !reason.isBlank()) {
            newValues.put("reason", reason);
        }

        auditLogService.record(
                actorUserId,
                "BOOKING_STATUS_TRANSITION",
                "Booking",
                saved.getPublicId(),
                oldValues,
                newValues
        );

        return saved;
    }

    public <T> IdempotencyService.ExecutionOutcome<T> executeIdempotentCommand(
            String scope,
            String actorKey,
            String idempotencyKey,
            Object requestPayload,
            Duration ttl,
            Class<T> responseType,
            Supplier<IdempotencyService.RecordedPayload<T>> operation) {
        return idempotencyService.execute(scope, actorKey, idempotencyKey, requestPayload, ttl, responseType, operation);
    }

    private void applyLifecycleTimestamps(Booking booking,
                                          BookingStatus targetStatus,
                                          BookingActorRole actorRole,
                                          Long actorUserId,
                                          String reason,
                                          Instant now) {
        switch (targetStatus) {
            case APPROVED -> {
                booking.setApprovedAt(now);
                booking.setApprovedBy(actorUserId);
            }
            case CONFIRMED -> {
                if (booking.getPaymentSummaryStatus() == PaymentSummaryStatus.UNPAID) {
                    booking.setPaymentSummaryStatus(
                            booking.getPickupBalanceDue().signum() > 0
                                    ? PaymentSummaryStatus.PARTIALLY_PAID
                                    : PaymentSummaryStatus.PAID_IN_FULL
                    );
                }
            }
            case COMPLETED -> booking.setCompletedAt(now);
            case CANCELLED, REJECTED -> {
                booking.setCancelledAt(now);
                booking.setCancelledBy(actorUserId);
                booking.setCancelledByRole(actorRole.name());
                booking.setCancellationReason(reason);
            }
            case NO_SHOW -> {
                booking.setNoShowAt(now);
                booking.setNoShowMarkedBy(actorUserId);
            }
            default -> {
                // Other states preserve existing lifecycle timestamps
            }
        }
    }
}
