package com.islandride.inventory.dto;

import java.time.Instant;

public record VehicleStatusHistoryDto(
        Long id,
        String fromStatus,
        String toStatus,
        String reason,
        Long changedBy,
        Instant createdAt
) {}

