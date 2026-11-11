package com.islandride.booking.service;

import com.islandride.booking.domain.GatewayFeeTreatment;
import com.islandride.booking.domain.SecurityDepositCollectionMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CancellationPolicySnapshot(
        Instant capturedAt,
        String currency,
        int cancellationDeadlineHours,
        boolean freeCancellationAllowed,
        BigDecimal defaultFeePercentage,
        List<CancellationTierSnapshot> tiers,
        int noShowGraceMinutes,
        BigDecimal noShowPenaltyPercentage,
        GatewayFeeTreatment gatewayFeeTreatment,
        boolean reschedulingAllowedForDisruption,
        SecurityDepositCollectionMethod depositCollectionMethod,
        BigDecimal securityDepositAmount
) {

    public record CancellationTierSnapshot(
            int minHoursBeforePickup,
            Integer maxHoursBeforePickup,
            BigDecimal feePercentage,
            BigDecimal flatFeeAmount,
            boolean appliesToDeposit,
            int priority
    ) {
    }
}

