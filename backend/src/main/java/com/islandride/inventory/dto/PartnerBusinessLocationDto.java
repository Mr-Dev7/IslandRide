package com.islandride.inventory.dto;

import com.islandride.geography.dto.LocationDto;

public record PartnerBusinessLocationDto(
        LocationDto location,
        boolean primary,
        boolean pickupAvailable,
        boolean returnAvailable
) {}

