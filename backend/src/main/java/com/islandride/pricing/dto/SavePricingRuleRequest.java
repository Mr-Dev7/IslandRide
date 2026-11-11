package com.islandride.pricing.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record SavePricingRuleRequest(
        @NotBlank(message = "Rule name is required")
        @Size(max = 100)
        String ruleName,

        @NotBlank(message = "Rule type is required")
        @Pattern(regexp = "^(DURATION_DISCOUNT|WEEKEND_SURGE|EARLY_BIRD)$", message = "Unsupported pricing rule type")
        String ruleType,

        @Min(1)
        @Max(365)
        int minDays,

        @NotBlank(message = "Adjustment type is required")
        @Pattern(regexp = "^(PERCENTAGE|FIXED_AMOUNT)$", message = "Adjustment type must be PERCENTAGE or FIXED_AMOUNT")
        String adjustmentType,

        @NotNull(message = "Adjustment value is required")
        @Digits(integer = 8, fraction = 2)
        BigDecimal adjustmentValue
) {}

