package com.islandride.geography.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveIslandRequest(
        @NotBlank(message = "Island name is required")
        @Size(max = 100)
        String name,

        @Size(max = 120)
        String displayAlias,

        @NotBlank(message = "Island code is required")
        @Pattern(regexp = "^[A-Z0-9_]{2,30}$", message = "Island code must contain uppercase letters, numbers, or underscores")
        String code,

        @Size(max = 1000)
        String description,

        Integer displayOrder,
        Boolean active
) {}

