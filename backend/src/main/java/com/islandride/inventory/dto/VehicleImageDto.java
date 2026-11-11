package com.islandride.inventory.dto;

import java.time.Instant;

public record VehicleImageDto(
        String publicId,
        String imageUrl,
        String originalFilename,
        String contentType,
        Long fileSizeBytes,
        boolean primary,
        int displayOrder,
        Instant createdAt
) {}

