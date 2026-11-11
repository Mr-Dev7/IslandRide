package com.islandride.pricing.dto;

import java.util.List;

public record VehiclePricingOverviewDto(
        VehiclePricingPlanDto activePlan,
        List<VehiclePricingPlanDto> history,
        List<PricingRuleDto> rules
) {}

