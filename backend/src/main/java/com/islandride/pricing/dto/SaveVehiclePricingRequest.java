package com.islandride.pricing.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

public record SaveVehiclePricingRequest(
        @Size(min = 3, max = 3)
        String currency,

        @DecimalMin(value = "0.00", inclusive = true, message = "Hourly rate cannot be negative")
        @Digits(integer = 8, fraction = 2)
        BigDecimal hourlyRate,

        @NotNull(message = "Daily rate is required")
        @DecimalMin(value = "1.00", message = "Daily rate must be at least 1.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal dailyRate,

        @DecimalMin(value = "0.00", inclusive = true, message = "Weekly rate cannot be negative")
        @Digits(integer = 8, fraction = 2)
        BigDecimal weeklyRate,

        @NotNull(message = "Security deposit is required")
        @DecimalMin(value = "0.00", inclusive = true, message = "Security deposit cannot be negative")
        @Digits(integer = 8, fraction = 2)
        BigDecimal securityDeposit,

        @Min(value = 1, message = "Minimum rental hours must be at least 1")
        @Max(value = 720, message = "Minimum rental hours cannot exceed 720")
        Integer minRentalHours,

        @Min(value = 1, message = "Maximum rental days must be at least 1")
        @Max(value = 365, message = "Maximum rental days cannot exceed 365")
        Integer maxRentalDays,

        @Min(0)
        Integer includedKmPerDay,

        @DecimalMin("0.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal extraKmCharge,

        @DecimalMin("0.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal extraHourCharge,

        @DecimalMin("0.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal cleaningFee,

        @DecimalMin("0.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal deliveryFee,

        @Size(max = 500)
        String additionalChargesDescription,

        @DecimalMin("0.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal lateReturnFeePerHour,

        @DecimalMin("0.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal lateReturnFlatPenalty,

        @Min(0)
        @Max(240)
        Integer lateReturnGraceMinutes,

        Instant validFrom,
        Instant validTo,
        Boolean active
) {}

