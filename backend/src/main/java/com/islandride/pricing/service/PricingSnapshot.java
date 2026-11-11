package com.islandride.pricing.service;

import com.islandride.booking.domain.PaymentMode;
import com.islandride.booking.domain.SecurityDepositCollectionMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PricingSnapshot(
        Instant quotedAt,
        String vehiclePublicId,
        String pricingPlanPublicId,
        String currency,
        Instant startTime,
        Instant endTime,
        long durationMinutes,
        String billingUnit,
        int billableDays,
        Integer billableHours,
        int billableWeeks,
        int remainderDays,
        BigDecimal hourlyRate,
        BigDecimal dailyRate,
        BigDecimal weeklyRate,
        BigDecimal baseRentalAmount,
        BigDecimal seasonalMultiplier,
        BigDecimal seasonalAdjustmentAmount,
        List<DailySeasonBreakdown> dailySeasons,
        List<AppliedRuleBreakdown> appliedRules,
        BigDecimal discountAmount,
        BigDecimal surchargeAmount,
        BigDecimal adjustedRentalSubtotal,
        BigDecimal cleaningFee,
        BigDecimal deliveryFee,
        BigDecimal commissionableRentalBase,
        BigDecimal gstPercentage,
        BigDecimal taxAmount,
        BigDecimal totalPayableAmount,
        BigDecimal securityDepositAmount,
        SecurityDepositCollectionMethod depositCollectionMethod,
        PaymentMode requestedPaymentMode,
        PaymentMode enforcedPaymentMode,
        BigDecimal advancePercentageApplied,
        BigDecimal advancePaidDueNow,
        BigDecimal pickupBalanceDue,
        BigDecimal commissionRateSnapshot,
        BigDecimal estimatedCommissionAmount,
        int turnaroundBufferMinutes,
        int fullPaymentCutoffHours
) {

    public record DailySeasonBreakdown(
            String date,
            String seasonName,
            BigDecimal multiplier
    ) {
    }

    public record AppliedRuleBreakdown(
            Long ruleId,
            String ruleName,
            String ruleType,
            String adjustmentType,
            BigDecimal adjustmentValue,
            int priority,
            boolean stackable,
            String exclusiveGroup,
            BigDecimal computedDelta
    ) {
    }
}

