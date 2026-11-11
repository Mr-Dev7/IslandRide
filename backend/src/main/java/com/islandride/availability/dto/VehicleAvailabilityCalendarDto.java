package com.islandride.availability.dto;

import java.time.Instant;
import java.util.List;

public record VehicleAvailabilityCalendarDto(
        String vehiclePublicId,
        Instant from,
        Instant to,
        boolean availableForRequestedWindow,
        String conflictReason,
        List<PublicAvailabilitySlotDto> unavailableSlots,
        List<AvailabilityBlockDto> detailedBlocks
) {}

