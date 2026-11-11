package com.islandride.inventory.dto;

import com.islandride.geography.dto.IslandDto;
import com.islandride.geography.dto.LocationDto;
import com.islandride.pricing.dto.VehiclePricingOverviewDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PartnerVehicleDto(
        String publicId,
        String businessPublicId,
        String businessTradeName,
        String businessVerificationStatus,
        IslandDto island,
        LocationDto pickupLocation,
        LocationDto returnLocation,
        boolean allowDifferentReturnSameIsland,
        VehicleCategoryDto category,
        String title,
        String make,
        String model,
        int modelYear,
        String description,
        String registrationNumber,
        String color,
        String transmissionType,
        String fuelType,
        int seatingCapacity,
        BigDecimal mileageKmpl,
        Integer engineCapacityCc,
        String vehicleCondition,
        String rentalInstructions,
        BigDecimal dailyRate,
        BigDecimal securityDeposit,
        int turnaroundBufferMinutes,
        String bookingMode,
        String status,
        String rejectionReason,
        String suspensionReason,
        Instant submittedAt,
        Instant approvedAt,
        Instant createdAt,
        Instant updatedAt,
        List<String> features,
        List<VehicleImageDto> images,
        List<VehicleDocumentDto> documents,
        VehiclePricingOverviewDto pricingOverview,
        List<VehicleStatusHistoryDto> statusHistory
) {}

