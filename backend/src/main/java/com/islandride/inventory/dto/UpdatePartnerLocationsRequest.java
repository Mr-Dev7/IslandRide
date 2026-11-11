package com.islandride.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdatePartnerLocationsRequest(
        String businessPublicId,

        @NotNull(message = "Locations list is required")
        @Valid
        List<LocationItem> locations
) {
    public record LocationItem(
            @NotBlank(message = "Location public ID is required")
            String locationPublicId,
            boolean primary,
            boolean pickupAvailable,
            boolean returnAvailable
    ) {}
}

