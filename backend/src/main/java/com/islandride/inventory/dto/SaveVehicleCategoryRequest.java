package com.islandride.inventory.dto;

import com.islandride.inventory.domain.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveVehicleCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(max = 80)
        String name,

        @NotBlank(message = "Category code is required")
        @Pattern(regexp = "^[A-Z0-9_]{2,40}$", message = "Category code must contain uppercase letters, numbers, or underscores")
        String code,

        @NotBlank(message = "Category slug is required")
        @Pattern(regexp = "^[a-z0-9-]{2,60}$", message = "Slug must contain lowercase letters, numbers, or hyphens")
        String slug,

        @NotNull(message = "Vehicle type is required")
        VehicleType vehicleType,

        @Size(max = 500)
        String description,

        @Size(max = 255)
        String iconRef,

        List<String> applicableAttributes,
        Integer displayOrder,
        Boolean active
) {}

