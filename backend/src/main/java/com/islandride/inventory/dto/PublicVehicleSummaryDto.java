package com.islandride.inventory.dto;

import com.islandride.geography.dto.LocationDto;

import java.math.BigDecimal;
import java.util.List;

public record PublicVehicleSummaryDto(
        String publicId,
        String title,
        String make,
        String model,
        int modelYear,
        Long categoryId,
        String categoryName,
        String categoryCode,
        String categorySlug,
        String vehicleType,
        Long islandId,
        String islandName,
        String islandDisplayAlias,
        String islandCode,
        LocationDto pickupLocation,
        LocationDto returnLocation,
        boolean allowDifferentReturnSameIsland,
        String transmissionType,
        String fuelType,
        int seatingCapacity,
        String vehicleCondition,
        BigDecimal dailyRate,
        BigDecimal securityDeposit,
        String currency,
        String bookingMode,
        String partnerPublicId,
        String partnerTradeName,
        boolean verifiedPartner,
        String primaryImageUrl,
        List<String> features
) {}

