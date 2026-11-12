package com.islandride.booking.service;

import com.islandride.booking.domain.Booking;
import com.islandride.booking.domain.BookingModification;
import com.islandride.booking.domain.BookingStatus;
import com.islandride.booking.domain.BookingStatusHistory;
import com.islandride.booking.domain.HandoverInspection;
import com.islandride.booking.domain.OverbookingExceptionRecord;
import com.islandride.booking.domain.SecurityDepositRecord;
import com.islandride.booking.dto.BookingDtos;
import com.islandride.booking.repository.BookingModificationRepository;
import com.islandride.booking.repository.BookingStatusHistoryRepository;
import com.islandride.booking.repository.HandoverInspectionRepository;
import com.islandride.booking.repository.SecurityDepositRecordRepository;
import com.islandride.commission.domain.Commission;
import com.islandride.commission.repository.CommissionRepository;
import com.islandride.paymentfinance.domain.OfflineCollection;
import com.islandride.paymentfinance.domain.Payment;
import com.islandride.paymentfinance.domain.PaymentWebhookEvent;
import com.islandride.paymentfinance.domain.Refund;
import com.islandride.paymentfinance.dto.PaymentDtos;
import com.islandride.paymentfinance.repository.OfflineCollectionRepository;
import com.islandride.paymentfinance.repository.PaymentRepository;
import com.islandride.paymentfinance.repository.RefundRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class BookingDtoMapper {

    private final SecurityDepositRecordRepository securityDepositRepository;
    private final CommissionRepository commissionRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OfflineCollectionRepository offlineCollectionRepository;
    private final HandoverInspectionRepository handoverInspectionRepository;
    private final BookingStatusHistoryRepository statusHistoryRepository;
    private final BookingModificationRepository modificationRepository;

    public BookingDtoMapper(SecurityDepositRecordRepository securityDepositRepository,
                            CommissionRepository commissionRepository,
                            PaymentRepository paymentRepository,
                            RefundRepository refundRepository,
                            OfflineCollectionRepository offlineCollectionRepository,
                            HandoverInspectionRepository handoverInspectionRepository,
                            BookingStatusHistoryRepository statusHistoryRepository,
                            BookingModificationRepository modificationRepository) {
        this.securityDepositRepository = securityDepositRepository;
        this.commissionRepository = commissionRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.offlineCollectionRepository = offlineCollectionRepository;
        this.handoverInspectionRepository = handoverInspectionRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.modificationRepository = modificationRepository;
    }

    public BookingDtos.BookingSummaryDto toSummary(Booking booking) {
        String customerName = booking.getUser() != null
                ? ((booking.getUser().getFirstName() != null ? booking.getUser().getFirstName() : "")
                + " " + (booking.getUser().getLastName() != null ? booking.getUser().getLastName() : "")).trim()
                : "";
        return new BookingDtos.BookingSummaryDto(
                booking.getPublicId(),
                booking.getBookingReference(),
                booking.getVehicle().getPublicId(),
                booking.getVehicle().getTitle(),
                booking.getBusiness().getPublicId(),
                booking.getBusiness().getTradeName(),
                customerName,
                booking.getUser() != null ? booking.getUser().getEmail() : null,
                booking.getPickupLocation() != null ? booking.getPickupLocation().getName() : null,
                booking.getReturnLocation() != null ? booking.getReturnLocation().getName() : null,
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getTotalDays(),
                booking.getStatus(),
                booking.getPaymentSummaryStatus(),
                booking.getOverbookingExceptionStatus(),
                booking.getBookingMode(),
                booking.getPaymentMode(),
                booking.getCurrency(),
                booking.getTotalPayableAmount(),
                booking.getAdvancePaidAmount(),
                booking.getPickupBalanceDue(),
                booking.getSecurityDepositAmount(),
                booking.getDepositCollectionMethod(),
                booking.getApprovalExpiresAt(),
                booking.getPaymentDeadlineAt(),
                booking.getCreatedAt()
        );
    }

    public BookingDtos.BookingDetailDto toDetail(Booking booking) {
        BookingDtos.BookingSummaryDto summary = toSummary(booking);

        SecurityDepositRecord depositRecord = securityDepositRepository.findByBookingId(booking.getId()).orElse(null);
        BookingDtos.SecurityDepositDto depositDto = depositRecord != null ? toDepositDto(depositRecord) : null;

        Commission commission = commissionRepository.findByBookingId(booking.getId()).orElse(null);
        BookingDtos.CommissionSummaryDto commissionDto = commission != null ? toCommissionDto(booking, commission) : null;

        List<PaymentDtos.PaymentSummaryDto> payments = paymentRepository.findByBookingIdOrderByCreatedAtDesc(booking.getId())
                .stream()
                .map(BookingDtoMapper::toPaymentDto)
                .toList();

        List<PaymentDtos.RefundSummaryDto> refunds = refundRepository.findByBookingIdOrderByCreatedAtDesc(booking.getId())
                .stream()
                .map(BookingDtoMapper::toRefundDto)
                .toList();

        List<BookingDtos.OfflineCollectionDto> offlineCollections = offlineCollectionRepository.findByBookingId(booking.getId())
                .stream()
                .map(BookingDtoMapper::toOfflineCollectionDto)
                .toList();

        List<BookingDtos.HandoverInspectionDto> inspections = handoverInspectionRepository.findByBookingId(booking.getId())
                .stream()
                .map(BookingDtoMapper::toInspectionDto)
                .toList();

        List<BookingDtos.BookingStatusHistoryDto> history = statusHistoryRepository
                .findByBookingIdOrderByCreatedAtAsc(booking.getId())
                .stream()
                .map(BookingDtoMapper::toHistoryDto)
                .toList();

        List<BookingDtos.BookingModificationDto> modifications = modificationRepository
                .findByBookingIdOrderByCreatedAtDesc(booking.getId())
                .stream()
                .map(BookingDtoMapper::toModificationDto)
                .toList();

        BigDecimal onlineDueNow = computeRemainingOnlineDue(booking);

        return new BookingDtos.BookingDetailDto(
                summary,
                booking.getCustomerDlNumber(),
                booking.getCustomerEmergencyContact(),
                booking.isDlConfirmed(),
                booking.getDailyRate(),
                booking.getHourlyRate(),
                booking.getBaseRentalAmount(),
                booking.getSeasonalMultiplier(),
                booking.getSeasonalAdjustmentAmount(),
                booking.getDiscountAmount(),
                booking.getSurchargeAmount(),
                booking.getCleaningFee(),
                booking.getDeliveryFee(),
                booking.getTaxAmount(),
                booking.getGstPercentage(),
                booking.getCommissionRateSnapshot(),
                booking.getAdvancePercentageApplied(),
                onlineDueNow,
                booking.getTurnaroundBufferMinutes(),
                booking.getPricingSnapshotJson(),
                booking.getCancellationPolicySnapshot(),
                booking.getApprovedAt(),
                booking.getCancelledAt(),
                booking.getCancelledByRole(),
                booking.getCancellationReason(),
                booking.getDisruptionExceptionType(),
                booking.getCompletedAt(),
                booking.getNoShowAt(),
                booking.getNoShowEvidenceNotes(),
                booking.getNoShowEvidenceRef(),
                depositDto,
                commissionDto,
                payments,
                refunds,
                offlineCollections,
                inspections,
                history,
                modifications
        );
    }

    public static BigDecimal computeRemainingOnlineDue(Booking booking) {
        if (booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.REJECTED
                || booking.getStatus() == BookingStatus.EXPIRED
                || booking.getStatus() == BookingStatus.NO_SHOW
                || booking.getStatus() == BookingStatus.COMPLETED) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = nonNegative(booking.getTotalPayableAmount());
        BigDecimal pickup = nonNegative(booking.getPickupBalanceDue());
        BigDecimal paid = nonNegative(booking.getAdvancePaidAmount());
        return total.subtract(pickup).subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public static BookingDtos.SecurityDepositDto toDepositDto(SecurityDepositRecord dep) {
        return new BookingDtos.SecurityDepositDto(
                dep.getPublicId(),
                dep.getCollectionMethod(),
                dep.getExpectedAmount(),
                dep.getCollectedAmount(),
                dep.getRefundedAmount(),
                dep.getForfeitedAmount(),
                dep.getStatus(),
                dep.getInspectionStatus(),
                dep.getInspectionDeadlineAt(),
                dep.getPaymentChannel(),
                dep.getReceiptReference(),
                dep.getCollectedAt(),
                dep.getReleasedAt(),
                dep.getNotes()
        );
    }

    public static BookingDtos.CommissionSummaryDto toCommissionDto(Booking booking, Commission c) {
        return new BookingDtos.CommissionSummaryDto(
                booking.getCurrency(),
                c.getGrossBookingAmount(),
                c.getTaxExcludedAmount(),
                c.getDepositExcludedAmount(),
                c.getCommissionPercentage(),
                c.getCommissionAmount(),
                c.getOnlineCollectedAmount(),
                c.getOfflineCollectedAmount(),
                c.getPartnerPayoutAmount(),
                c.getPartnerReceivableAmount(),
                c.getStatus().name()
        );
    }

    public static PaymentDtos.PaymentSummaryDto toPaymentDto(Payment p) {
        String bookingPid = null;
        String bookingRef = null;
        try {
            if (p.getBooking() != null) {
                bookingPid = p.getBooking().getPublicId();
                bookingRef = p.getBooking().getBookingReference();
            }
        } catch (Exception ignored) {}

        return new PaymentDtos.PaymentSummaryDto(
                p.getPublicId(),
                bookingPid,
                bookingRef,
                p.getAmount(),
                p.getCurrency(),
                p.getPaymentType(),
                p.isDeposit(),
                p.getPaymentMethod(),
                p.getGatewayProvider(),
                p.getGatewayOrderId(),
                p.getGatewayPaymentId(),
                p.getStatus(),
                p.getGatewayOrderExpiresAt(),
                p.getCapturedAt(),
                p.getFailedAt(),
                p.getFailureReason(),
                p.getCreatedAt()
        );
    }

    public static PaymentDtos.RefundSummaryDto toRefundDto(Refund r) {
        String paymentPid = null;
        try {
            if (r.getPayment() != null) {
                paymentPid = r.getPayment().getPublicId();
            }
        } catch (Exception ignored) {}

        String bookingPid = null;
        String bookingRef = null;
        try {
            if (r.getBooking() != null) {
                bookingPid = r.getBooking().getPublicId();
                bookingRef = r.getBooking().getBookingReference();
            }
        } catch (Exception ignored) {}

        return new PaymentDtos.RefundSummaryDto(
                r.getPublicId(),
                paymentPid,
                bookingPid,
                bookingRef,
                r.getAmount(),
                r.getGatewayFeeRetained(),
                r.getCurrency(),
                r.getReason(),
                r.getRefundType(),
                r.getGatewayRefundId(),
                r.getStatus(),
                r.getRetryCount(),
                r.getNextRetryAt(),
                r.getErrorMessage(),
                r.getEscalationTicketRef(),
                r.getReconciledAt(),
                r.getCreatedAt()
        );
    }

    public static PaymentDtos.WebhookEventSummaryDto toWebhookDto(PaymentWebhookEvent w) {
        return new PaymentDtos.WebhookEventSummaryDto(
                w.getPublicId(),
                w.getGatewayProvider(),
                w.getGatewayEventId(),
                w.getEventType(),
                w.isSignatureVerified(),
                w.getProcessingStatus(),
                w.getBooking() != null ? w.getBooking().getPublicId() : null,
                w.getPayment() != null ? w.getPayment().getPublicId() : null,
                w.getErrorMessage(),
                w.getReceivedAt(),
                w.getProcessedAt()
        );
    }

    public static BookingDtos.OverbookingExceptionDto toOverbookingDto(OverbookingExceptionRecord r) {
        return new BookingDtos.OverbookingExceptionDto(
                r.getPublicId(),
                r.getBooking().getPublicId(),
                r.getBooking().getBookingReference(),
                r.getConflictingBooking() != null ? r.getConflictingBooking().getPublicId() : null,
                r.getConflictingBooking() != null ? r.getConflictingBooking().getBookingReference() : null,
                r.getPaymentId(),
                r.getStatus().name(),
                r.getResolutionNotes(),
                r.getDetectedAt(),
                r.getResolvedAt()
        );
    }

    public static BookingDtos.OfflineCollectionDto toOfflineCollectionDto(OfflineCollection o) {
        return new BookingDtos.OfflineCollectionDto(
                o.getId(),
                o.getAmount(),
                o.getCollectionType(),
                o.getPaymentMethod(),
                o.getReceiptReference(),
                o.getNotes(),
                o.getCollectedAt()
        );
    }

    public static BookingDtos.HandoverInspectionDto toInspectionDto(HandoverInspection h) {
        return new BookingDtos.HandoverInspectionDto(
                h.getPublicId(),
                h.getHandoverType(),
                h.getStaffUser().getId(),
                h.getOdometerReading(),
                h.getFuelPercentage(),
                h.getChecklistData(),
                h.getNotes(),
                h.getCompletedAt()
        );
    }

    public static BookingDtos.BookingStatusHistoryDto toHistoryDto(BookingStatusHistory h) {
        return new BookingDtos.BookingStatusHistoryDto(
                parseStatus(h.getFromStatus()),
                parseStatus(h.getToStatus()),
                h.getChangeReason(),
                h.getChangedBy(),
                h.getCreatedAt()
        );
    }

    private static BookingStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return BookingStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static BookingDtos.BookingModificationDto toModificationDto(BookingModification m) {
        return new BookingDtos.BookingModificationDto(
                m.getPublicId(),
                m.getPreviousStartTime(),
                m.getPreviousEndTime(),
                m.getNewStartTime(),
                m.getNewEndTime(),
                m.getPreviousPickupLocation().getName(),
                m.getNewPickupLocation().getName(),
                m.getPreviousReturnLocation().getName(),
                m.getNewReturnLocation().getName(),
                m.getPreviousTotalPayable(),
                m.getNewTotalPayable(),
                m.getPriceDelta(),
                m.getAdjustmentType(),
                m.getReason(),
                m.getCreatedAt()
        );
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value != null && value.signum() > 0 ? value : BigDecimal.ZERO;
    }
}
