package com.islandride.pricing.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record VehiclePricingPlanDto(
        String publicId,
        String currency,
        BigDecimal hourlyRate,
        BigDecimal dailyRate,
        BigDecimal weeklyRate,
        BigDecimal securityDeposit,
        int minRentalHours,
        int maxRentalDays,
        Integer includedKmPerDay,
        BigDecimal extraKmCharge,
        BigDecimal extraHourCharge,
        BigDecimal cleaningFee,
        BigDecimal deliveryFee,
        String additionalChargesDescription,
        BigDecimal lateReturnFeePerHour,
        BigDecimal lateReturnFlatPenalty,
        int lateReturnGraceMinutes,
        Instant validFrom,
        Instant validTo,
        boolean active,
        BigDecimal gstPercentage
) {}

