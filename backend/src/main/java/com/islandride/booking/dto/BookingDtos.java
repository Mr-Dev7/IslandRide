package com.islandride.booking.dto;

import com.islandride.availability.domain.HoldPurpose;
import com.islandride.availability.domain.HoldStatus;
import com.islandride.inventory.domain.BookingMode;
import com.islandride.booking.domain.BookingStatus;
import com.islandride.booking.domain.DepositInspectionStatus;
import com.islandride.booking.domain.DisruptionExceptionType;
import com.islandride.booking.domain.HandoverType;
import com.islandride.booking.domain.OverbookingExceptionStatus;
import com.islandride.booking.domain.PaymentMode;
import com.islandride.booking.domain.PaymentSummaryStatus;
import com.islandride.booking.domain.SecurityDepositCollectionMethod;
import com.islandride.booking.domain.SecurityDepositStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class BookingDtos {

    private BookingDtos() {
    }

    public record PricePreviewResponse(
            String vehiclePublicId,
            String vehicleTitle,
            BookingMode bookingMode,
            boolean available,
            String conflictReason,
            Instant startTime,
            Instant endTime,
            String pickupLocationPublicId,
            String pickupLocationName,
            String returnLocationPublicId,
            String returnLocationName,
            String currency,
            String billingUnit,
            int billableDays,
            Integer billableHours,
            BigDecimal dailyRate,
            BigDecimal hourlyRate,
            BigDecimal baseRentalAmount,
            BigDecimal seasonalMultiplier,
            BigDecimal seasonalAdjustmentAmount,
            BigDecimal discountAmount,
            BigDecimal surchargeAmount,
            BigDecimal cleaningFee,
            BigDecimal deliveryFee,
            BigDecimal gstPercentage,
            BigDecimal taxAmount,
            BigDecimal totalPayableAmount,
            BigDecimal securityDepositAmount,
            SecurityDepositCollectionMethod depositCollectionMethod,
            PaymentMode requestedPaymentMode,
            PaymentMode enforcedPaymentMode,
            BigDecimal advancePercentageApplied,
            BigDecimal advanceDueNow,
            BigDecimal pickupBalanceDue,
            int turnaroundBufferMinutes,
            int fullPaymentCutoffHours,
            int holdDurationSeconds,
            int cancellationDeadlineHours,
            boolean freeCancellationAllowed,
            BigDecimal cancellationFeePercentage
    ) {
    }

    public record AcquireHoldRequest(
            @jakarta.validation.constraints.NotBlank String vehiclePublicId,
            @jakarta.validation.constraints.NotNull Instant startTime,
            @jakarta.validation.constraints.NotNull Instant endTime,
            String pickupLocationPublicId,
            String returnLocationPublicId,
            HoldPurpose purpose,
            String idempotencyKey
    ) {
    }

    public record InventoryHoldResponse(
            String publicId,
            String vehiclePublicId,
            String vehicleTitle,
            String partnerTradeName,
            String islandName,
            BookingMode bookingMode,
            HoldPurpose purpose,
            HoldStatus status,
            String pickupLocationPublicId,
            String pickupLocationName,
            String returnLocationPublicId,
            String returnLocationName,
            Instant startTime,
            Instant endTime,
            int turnaroundBufferMinutes,
            int holdDurationSeconds,
            Instant expiresAt,
            long remainingSeconds,
            PricePreviewResponse fullQuote,
            PricePreviewResponse partialQuote
    ) {
    }

    public record CreateBookingRequest(
            @jakarta.validation.constraints.NotBlank String holdPublicId,
            PaymentMode paymentMode,
            boolean deliveryRequested,
            @jakarta.validation.constraints.NotBlank String customerDlNumber,
            @jakarta.validation.constraints.NotBlank String customerEmergencyContact,
            boolean dlConfirmed,
            String idempotencyKey
    ) {
    }

    public record ModifyBookingRequest(
            @jakarta.validation.constraints.NotNull Instant newStartTime,
            @jakarta.validation.constraints.NotNull Instant newEndTime,
            String pickupLocationPublicId,
            String returnLocationPublicId,
            Boolean deliveryRequested,
            String reason
    ) {
    }

    public record CancelBookingRequest(
            String reason
    ) {
    }

    public record PartnerApprovalDecisionRequest(
            String reason
    ) {
    }

    public record PickupHandoverRequest(
            @jakarta.validation.constraints.Min(0) int odometerReading,
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(100) int fuelPercentage,
            String checklistDataJson,
            String notes,
            BigDecimal balanceCollectedAmount,
            String balancePaymentMethod,
            String balanceReceiptReference,
            BigDecimal depositCollectedAmount,
            String depositPaymentMethod,
            String depositReceiptReference,
            boolean depositWaived,
            List<HandoverEvidenceInput> evidence
    ) {
    }

    public record ReturnCompleteRequest(
            @jakarta.validation.constraints.Min(0) int odometerReading,
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(100) int fuelPercentage,
            String checklistDataJson,
            String notes,
            boolean damageReported,
            BigDecimal damageClaimAmount,
            boolean releaseDepositImmediately,
            List<HandoverEvidenceInput> evidence
    ) {
    }

    public record HandoverEvidenceInput(
            String evidenceType,
            String imageS3Key,
            String mediaMimeType
    ) {
    }

    public record NoShowRequest(
            String evidenceNotes,
            String evidenceRef
    ) {
    }

    public record DisruptionOverrideRequest(
            @jakarta.validation.constraints.NotNull DisruptionExceptionType disruptionType,
            @jakarta.validation.constraints.NotBlank String reason,
            boolean preferReschedule
    ) {
    }

    public record DisputeResolveRequest(
            @jakarta.validation.constraints.NotNull BookingStatus targetStatus,
            @jakarta.validation.constraints.NotBlank String resolutionNotes,
            BigDecimal refundAmount,
            BigDecimal depositForfeitAmount
    ) {
    }

    public record DepositSettlementRequest(
            BigDecimal releaseAmount,
            BigDecimal forfeitAmount,
            String notes
    ) {
    }

    public record ResolveOverbookingRequest(
            @jakarta.validation.constraints.NotBlank String resolutionNotes
    ) {
    }

    public record CancellationPreviewResponse(
            String bookingPublicId,
            BookingStatus currentStatus,
            BigDecimal totalPayableAmount,
            BigDecimal rentalOnlinePaid,
            BigDecimal depositOnlinePaid,
            BigDecimal rentalPenaltyAmount,
            BigDecimal gatewayFeeDeducted,
            BigDecimal netRentalRefundAmount,
            BigDecimal securityDepositRefundAmount,
            BigDecimal totalRefundAmount,
            boolean depositProtected,
            boolean reschedulingEligible,
            String policyAppliedDescription,
            Instant cancellationEvaluatedAt
    ) {
    }

    public record BookingSummaryDto(
            String publicId,
            String bookingReference,
            String vehiclePublicId,
            String vehicleTitle,
            String businessPublicId,
            String partnerTradeName,
            String customerName,
            String customerEmail,
            String pickupLocationName,
            String returnLocationName,
            Instant startTime,
            Instant endTime,
            int totalDays,
            BookingStatus status,
            PaymentSummaryStatus paymentSummaryStatus,
            OverbookingExceptionStatus overbookingExceptionStatus,
            BookingMode bookingMode,
            PaymentMode paymentMode,
            String currency,
            BigDecimal totalPayableAmount,
            BigDecimal advancePaidAmount,
            BigDecimal pickupBalanceDue,
            BigDecimal securityDepositAmount,
            SecurityDepositCollectionMethod depositCollectionMethod,
            Instant approvalExpiresAt,
            Instant paymentDeadlineAt,
            Instant createdAt
    ) {
    }

    public record BookingStatusHistoryDto(
            BookingStatus fromStatus,
            BookingStatus toStatus,
            String reason,
            Long changedBy,
            Instant createdAt
    ) {
    }

    public record BookingModificationDto(
            String publicId,
            Instant previousStartTime,
            Instant previousEndTime,
            Instant newStartTime,
            Instant newEndTime,
            String previousPickupLocationName,
            String newPickupLocationName,
            String previousReturnLocationName,
            String newReturnLocationName,
            BigDecimal previousTotalPayable,
            BigDecimal newTotalPayable,
            BigDecimal priceDelta,
            String adjustmentType,
            String reason,
            Instant createdAt
    ) {
    }

    public record HandoverInspectionDto(
            String publicId,
            HandoverType handoverType,
            Long staffUserId,
            int odometerReading,
            int fuelPercentage,
            String checklistData,
            String notes,
            Instant completedAt
    ) {
    }

    public record SecurityDepositDto(
            String publicId,
            SecurityDepositCollectionMethod collectionMethod,
            BigDecimal expectedAmount,
            BigDecimal collectedAmount,
            BigDecimal refundedAmount,
            BigDecimal forfeitedAmount,
            SecurityDepositStatus status,
            DepositInspectionStatus inspectionStatus,
            Instant inspectionDeadlineAt,
            String paymentChannel,
            String receiptReference,
            Instant collectedAt,
            Instant releasedAt,
            String notes
    ) {
    }

    public record OfflineCollectionDto(
            Long id,
            BigDecimal amount,
            String collectionType,
            String paymentMethod,
            String receiptReference,
            String notes,
            Instant collectedAt
    ) {
    }

    public record CommissionSummaryDto(
            String currency,
            BigDecimal grossBookingAmount,
            BigDecimal taxExcludedAmount,
            BigDecimal depositExcludedAmount,
            BigDecimal commissionRatePercent,
            BigDecimal commissionAmount,
            BigDecimal onlineCollectedAmount,
            BigDecimal offlineCollectedAmount,
            BigDecimal partnerPayoutAmount,
            BigDecimal partnerReceivableAmount,
            String status
    ) {
    }

    public record OverbookingExceptionDto(
            String publicId,
            String bookingPublicId,
            String bookingReference,
            String conflictingBookingPublicId,
            String conflictingBookingReference,
            Long paymentId,
            String status,
            String resolutionNotes,
            Instant detectedAt,
            Instant resolvedAt
    ) {
    }

    public record BookingDetailDto(
            BookingSummaryDto summary,
            String customerDlNumber,
            String customerEmergencyContact,
            boolean dlConfirmed,
            BigDecimal dailyRate,
            BigDecimal hourlyRate,
            BigDecimal baseRentalAmount,
            BigDecimal seasonalMultiplier,
            BigDecimal seasonalAdjustmentAmount,
            BigDecimal discountAmount,
            BigDecimal surchargeAmount,
            BigDecimal cleaningFee,
            BigDecimal deliveryFee,
            BigDecimal taxAmount,
            BigDecimal gstPercentage,
            BigDecimal commissionRateSnapshot,
            BigDecimal advancePercentageApplied,
            BigDecimal onlineAmountDueNow,
            int turnaroundBufferMinutes,
            String pricingSnapshotJson,
            String cancellationPolicySnapshotJson,
            Instant approvedAt,
            Instant cancelledAt,
            String cancelledByRole,
            String cancellationReason,
            DisruptionExceptionType disruptionExceptionType,
            Instant completedAt,
            Instant noShowAt,
            String noShowEvidenceNotes,
            String noShowEvidenceRef,
            SecurityDepositDto securityDeposit,
            CommissionSummaryDto commission,
            List<com.islandride.paymentfinance.dto.PaymentDtos.PaymentSummaryDto> payments,
            List<com.islandride.paymentfinance.dto.PaymentDtos.RefundSummaryDto> refunds,
            List<OfflineCollectionDto> offlineCollections,
            List<HandoverInspectionDto> inspections,
            List<BookingStatusHistoryDto> statusHistory,
            List<BookingModificationDto> modifications
    ) {
    }
}
