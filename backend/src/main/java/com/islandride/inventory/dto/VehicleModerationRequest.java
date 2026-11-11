package com.islandride.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VehicleModerationRequest(
        @NotBlank(message = "Moderation reason is required")
        @Size(max = 2000, message = "Reason cannot exceed 2000 characters")
        String reason
) {}

