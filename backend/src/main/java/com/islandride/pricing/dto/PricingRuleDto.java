package com.islandride.pricing.dto;

import java.math.BigDecimal;

public record PricingRuleDto(
        Long id,
        String ruleName,
        String ruleType,
        int minDays,
        String adjustmentType,
        BigDecimal adjustmentValue,
        boolean active
) {}

