package com.islandride.availability.dto;

import java.time.Instant;

public record AvailabilityBlockDto(
        String publicId,
        String vehiclePublicId,
        String blockType,
        Instant startTime,
        Instant endTime,
        String reason,
        String status,
        String notes,
        String restrictionDetails,
        boolean removable,
        Instant createdAt,
        Instant cancelledAt
) {}

