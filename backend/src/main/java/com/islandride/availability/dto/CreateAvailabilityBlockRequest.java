package com.islandride.availability.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateAvailabilityBlockRequest(
        @NotNull(message = "Start time is required")
        Instant startTime,

        @NotNull(message = "End time is required")
        Instant endTime,

        @Pattern(regexp = "^(MANUAL_BLOCK|MAINTENANCE|DAMAGE_REPAIR|OPERATIONAL_RESTRICTION)$",
                message = "Block type must be MANUAL_BLOCK, MAINTENANCE, DAMAGE_REPAIR, or OPERATIONAL_RESTRICTION")
        String blockType,

        @NotBlank(message = "Reason is required")
        @Size(max = 50, message = "Reason cannot exceed 50 characters")
        String reason,

        @Size(max = 2000, message = "Notes cannot exceed 2000 characters")
        String notes,

        @Size(max = 255, message = "Restriction details cannot exceed 255 characters")
        String restrictionDetails,

        Boolean removable
) {}

