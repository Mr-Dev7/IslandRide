package com.islandride.inventory.dto;

import com.islandride.inventory.domain.BookingMode;
import com.islandride.inventory.domain.FuelType;
import com.islandride.inventory.domain.TransmissionType;
import com.islandride.inventory.domain.VehicleCondition;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record SaveVehicleRequest(
        @Size(max = 36)
        String businessPublicId,

        @NotNull(message = "Island ID is required")
        Long islandId,

        @Size(max = 36)
        String pickupLocationPublicId,

        @Size(max = 36)
        String returnLocationPublicId,

        Boolean allowDifferentReturnSameIsland,

        @NotNull(message = "Category ID is required")
        Long categoryId,

        @NotBlank(message = "Vehicle title is required")
        @Size(max = 150, message = "Title cannot exceed 150 characters")
        String title,

        @NotBlank(message = "Make/Brand is required")
        @Size(max = 80, message = "Make cannot exceed 80 characters")
        String make,

        @NotBlank(message = "Model is required")
        @Size(max = 80, message = "Model cannot exceed 80 characters")
        String model,

        @NotNull(message = "Model year is required")
        @Min(value = 1995, message = "Model year must be 1995 or later")
        @Max(value = 2035, message = "Model year is out of range")
        Integer modelYear,

        @Size(max = 4000, message = "Description cannot exceed 4000 characters")
        String description,

        @NotBlank(message = "Registration number is required")
        @Size(max = 20, message = "Registration number cannot exceed 20 characters")
        String registrationNumber,

        @Size(max = 40)
        String color,

        @NotNull(message = "Transmission type is required")
        TransmissionType transmissionType,

        @NotNull(message = "Fuel type is required")
        FuelType fuelType,

        @NotNull(message = "Seating capacity is required")
        @Min(value = 1, message = "Seating capacity must be at least 1")
        @Max(value = 50, message = "Seating capacity cannot exceed 50")
        Integer seatingCapacity,

        @DecimalMin(value = "0.00", message = "Mileage cannot be negative")
        @Digits(integer = 4, fraction = 2)
        BigDecimal mileageKmpl,

        @Min(value = 0, message = "Engine capacity cannot be negative")
        @Max(value = 10000)
        Integer engineCapacityCc,

        VehicleCondition vehicleCondition,

        @Size(max = 3000)
        String rentalInstructions,

        @NotNull(message = "Daily rate is required")
        @DecimalMin(value = "1.00", message = "Daily rate must be at least 1.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal dailyRate,

        @DecimalMin(value = "0.00", message = "Security deposit cannot be negative")
        @Digits(integer = 8, fraction = 2)
        BigDecimal securityDeposit,

        @Min(value = 0, message = "Turnaround buffer cannot be negative")
        @Max(value = 1440)
        Integer turnaroundBufferMinutes,

        BookingMode bookingMode,

        List<@NotBlank @Size(max = 80) String> features
) {}

