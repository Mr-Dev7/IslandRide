package com.islandride.inventory.dto;

import com.islandride.geography.dto.IslandDto;
import com.islandride.geography.dto.LocationDto;
import com.islandride.pricing.dto.PricingRuleDto;
import com.islandride.pricing.dto.VehiclePricingPlanDto;

import java.math.BigDecimal;
import java.util.List;

public record PublicVehicleDetailDto(
        String publicId,
        String title,
        String make,
        String model,
        int modelYear,
        String description,
        String color,
        VehicleCategoryDto category,
        IslandDto island,
        LocationDto pickupLocation,
        LocationDto returnLocation,
        boolean allowDifferentReturnSameIsland,
        String transmissionType,
        String fuelType,
        int seatingCapacity,
        BigDecimal mileageKmpl,
        Integer engineCapacityCc,
        String vehicleCondition,
        String rentalInstructions,
        String bookingMode,
        int turnaroundBufferMinutes,
        String partnerPublicId,
        String partnerTradeName,
        boolean verifiedPartner,
        List<String> features,
        List<PublicVehicleImageDto> images,
        VehiclePricingPlanDto pricingPlan,
        List<PricingRuleDto> pricingRules
) {}

