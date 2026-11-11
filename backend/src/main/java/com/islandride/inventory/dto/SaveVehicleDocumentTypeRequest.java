package com.islandride.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveVehicleDocumentTypeRequest(
        @NotBlank(message = "Document type code is required")
        @Pattern(regexp = "^[A-Z0-9_]{2,40}$", message = "Code must contain uppercase letters, numbers, or underscores")
        String code,

        @NotBlank(message = "Document type name is required")
        @Size(max = 100)
        String name,

        @Size(max = 255)
        String description,

        Boolean mandatory,
        Boolean requiresExpiryDate,

        @Size(max = 100)
        String applicableVehicleTypes,

        Boolean active,
        Integer displayOrder
) {}

