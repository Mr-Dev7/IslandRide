package com.islandride.paymentfinance.dto;

import com.islandride.paymentfinance.domain.PaymentMethod;
import com.islandride.paymentfinance.domain.PaymentStatus;
import com.islandride.paymentfinance.domain.PaymentType;
import com.islandride.paymentfinance.domain.RefundStatus;
import com.islandride.paymentfinance.domain.RefundType;
import com.islandride.paymentfinance.domain.WebhookProcessingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record GatewayCheckoutOrderResponse(
            String paymentPublicId,
            String bookingPublicId,
            String bookingReference,
            String gatewayProvider,
            String gatewayOrderId,
            BigDecimal amount,
            String currency,
            PaymentType paymentType,
            boolean liveEnabled,
            Instant orderExpiresAt,
            Instant bookingPaymentDeadlineAt
    ) {
    }

    public record VerifyPaymentRequest(
            @jakarta.validation.constraints.NotBlank String bookingPublicId,
            @jakarta.validation.constraints.NotBlank String gatewayOrderId,
            @jakarta.validation.constraints.NotBlank String gatewayPaymentId,
            @jakarta.validation.constraints.NotBlank String gatewaySignature
    ) {
    }

    public record PaymentSummaryDto(
            String publicId,
            String bookingPublicId,
            String bookingReference,
            BigDecimal amount,
            String currency,
            PaymentType paymentType,
            boolean deposit,
            PaymentMethod paymentMethod,
            String gatewayProvider,
            String gatewayOrderId,
            String gatewayPaymentId,
            PaymentStatus status,
            Instant gatewayOrderExpiresAt,
            Instant capturedAt,
            Instant failedAt,
            String failureReason,
            Instant createdAt
    ) {
    }

    public record PaymentVerificationResponse(
            String bookingPublicId,
            String bookingReference,
            String bookingStatus,
            String paymentSummaryStatus,
            PaymentSummaryDto payment,
            boolean overbookingDetected
    ) {
    }

    public record RefundSummaryDto(
            String publicId,
            String paymentPublicId,
            String bookingPublicId,
            String bookingReference,
            BigDecimal amount,
            BigDecimal gatewayFeeRetained,
            String currency,
            String reason,
            RefundType refundType,
            String gatewayRefundId,
            RefundStatus status,
            int retryCount,
            Instant nextRetryAt,
            String errorMessage,
            String escalationTicketRef,
            Instant reconciledAt,
            Instant createdAt
    ) {
    }

    public record ManualRefundRequest(
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.01") BigDecimal amount,
            @jakarta.validation.constraints.NotBlank String reason,
            RefundType refundType
    ) {
    }

    public record WebhookEventSummaryDto(
            String publicId,
            String gatewayProvider,
            String gatewayEventId,
            String eventType,
            boolean signatureVerified,
            WebhookProcessingStatus processingStatus,
            String bookingPublicId,
            String paymentPublicId,
            String errorMessage,
            Instant receivedAt,
            Instant processedAt
    ) {
    }

    public record SecurityDepositTrustSummaryDto(
            BigDecimal totalExpectedDeposits,
            BigDecimal totalCollectedHeldLiability,
            BigDecimal totalInspectionHoldAmount,
            BigDecimal totalReleasedDeposits,
            BigDecimal totalForfeitedDeposits,
            long activeHeldCount,
            long inspectionHoldCount
    ) {
    }

    public record AdminFinanceOverviewDto(
            BigDecimal totalOnlineRentalCaptured,
            BigDecimal totalOfflineRentalRecorded,
            BigDecimal totalPlatformCommission,
            BigDecimal totalPendingRefundAmount,
            BigDecimal totalCompletedRefundAmount,
            long failedRefundsCount,
            long openOverbookingExceptionsCount,
            SecurityDepositTrustSummaryDto depositTrustSummary,
            List<PaymentSummaryDto> recentPayments,
            List<RefundSummaryDto> recentRefunds,
            List<WebhookEventSummaryDto> recentWebhooks,
            List<com.islandride.booking.dto.BookingDtos.OverbookingExceptionDto> overbookingExceptions
    ) {
    }

    public record MockPaymentSimulateRequest(
            @jakarta.validation.constraints.NotBlank String gatewayOrderId,
            String outcome, // CAPTURE_SUCCESS, PAYMENT_FAILED, WEBHOOK_CAPTURE
            String failureReason
    ) {
    }

    public record MockPaymentSimulateResponse(
            String gatewayOrderId,
            String gatewayPaymentId,
            String checkoutSignature,
            String webhookEventId,
            String webhookPayload,
            String webhookSignature,
            String outcome
    ) {
    }
}
