package com.islandride.geography.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SaveLocationRequest(
        @NotNull(message = "Island ID is required")
        Long islandId,

        @NotBlank(message = "Location name is required")
        @Size(max = 150)
        String name,

        @NotBlank(message = "Location code is required")
        @Pattern(regexp = "^[A-Z0-9_]{2,30}$", message = "Location code must contain uppercase letters, numbers, or underscores")
        String code,

        @NotBlank(message = "Address is required")
        @Size(max = 500)
        String address,

        BigDecimal latitude,
        BigDecimal longitude,

        @Size(max = 20)
        String contactPhone,

        Boolean active
) {}

