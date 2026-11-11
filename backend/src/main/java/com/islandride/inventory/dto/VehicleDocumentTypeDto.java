package com.islandride.inventory.dto;

public record VehicleDocumentTypeDto(
        Long id,
        String code,
        String name,
        String description,
        boolean mandatory,
        boolean requiresExpiryDate,
        String applicableVehicleTypes,
        boolean active,
        int displayOrder
) {}

