package com.islandride.pricing.service;

import com.islandride.booking.domain.GatewayFeeTreatment;
import com.islandride.booking.domain.SecurityDepositCollectionMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public record EffectiveBookingPolicy(
        String currency,
        BigDecimal gstPercentage,
        BigDecimal commissionPercentage,
        int minRentalHours,
        int maxRentalDays,
        int turnaroundBufferMinutes,
        BigDecimal advancePaymentPercentage,
        int fullPaymentCutoffHours,
        int cancellationDeadlineHours,
        boolean freeCancellationAllowed,
        BigDecimal cancellationFeePercentage,
        int depositInspectionWindowHours,
        SecurityDepositCollectionMethod depositCollectionMethod,
        int noShowGraceMinutes,
        BigDecimal noShowPenaltyPercentage,
        GatewayFeeTreatment gatewayFeeTreatment,
        boolean reschedulingAllowedForDisruption,
        int minLeadTimeMinutes,
        int holdDurationSeconds,
        int approvalWindowGt24hHours,
        int approvalWindow6To24hHours,
        int approvalWindowLt6hMinutes,
        int approvedPaymentWindowHours,
        int approvedPaymentWindowUrgentMinutes,
        int approvedPaymentWindowUrgentThresholdHours,
        ZoneId operatingHoursTimezone,
        LocalTime defaultOpenTime,
        LocalTime defaultCloseTime
) {

    public Instant computeApprovalExpiresAt(Instant requestTime, Instant pickupStart) {
        long minutesUntilPickup = ChronoUnit.MINUTES.between(requestTime, pickupStart);
        Instant candidate;
        if (minutesUntilPickup > 24L * 60L) {
            candidate = requestTime.plus(approvalWindowGt24hHours, ChronoUnit.HOURS);
        } else if (minutesUntilPickup >= 6L * 60L) {
            candidate = requestTime.plus(approvalWindow6To24hHours, ChronoUnit.HOURS);
        } else {
            candidate = requestTime.plus(approvalWindowLt6hMinutes, ChronoUnit.MINUTES);
        }
        return candidate.isAfter(pickupStart) ? pickupStart : candidate;
    }

    public Instant computePostApprovalPaymentDeadline(Instant approvedAt, Instant pickupStart) {
        long minutesUntilPickup = ChronoUnit.MINUTES.between(approvedAt, pickupStart);
        Instant candidate;
        if (minutesUntilPickup < approvedPaymentWindowUrgentThresholdHours * 60L) {
            candidate = approvedAt.plus(approvedPaymentWindowUrgentMinutes, ChronoUnit.MINUTES);
        } else {
            candidate = approvedAt.plus(approvedPaymentWindowHours, ChronoUnit.HOURS);
        }
        return candidate.isAfter(pickupStart) ? pickupStart : candidate;
    }
}

