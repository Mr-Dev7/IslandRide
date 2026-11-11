package com.islandride.availability.dto;

import java.time.Instant;

public record PublicAvailabilitySlotDto(
        Instant startTime,
        Instant endTime,
        String slotType,
        String displayLabel
) {}

