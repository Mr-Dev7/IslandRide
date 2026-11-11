package com.islandride.inventory.dto;

public record PublicVehicleImageDto(
        String publicId,
        String imageUrl,
        boolean primary,
        int displayOrder
) {}

