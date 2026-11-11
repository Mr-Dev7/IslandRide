package com.islandride.inventory.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReorderVehicleImagesRequest(
        @NotEmpty(message = "Ordered image IDs list cannot be empty")
        List<String> imagePublicIds
) {}

