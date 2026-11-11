package com.islandride.booking.service;

import com.islandride.booking.domain.DisruptionExceptionType;

import java.math.BigDecimal;

public record CancellationSettlementBreakdown(
        BigDecimal rentalPenaltyAmount,
        BigDecimal rentalRefundBeforeGatewayFee,
        BigDecimal gatewayFeeDeducted,
        BigDecimal netRentalRefundAmount,
        BigDecimal securityDepositRefundAmount,
        BigDecimal totalRefundAmount,
        boolean depositProtected,
        boolean reschedulingEligible,
        DisruptionExceptionType disruptionExceptionType,
        String policyAppliedDescription
) {
}

