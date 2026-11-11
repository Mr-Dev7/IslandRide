package com.islandride.inventory.dto;

import com.islandride.inventory.domain.VehicleType;

import java.util.List;

public record VehicleCategoryDto(
        Long id,
        String name,
        String code,
        String slug,
        VehicleType vehicleType,
        String description,
        String iconRef,
        List<String> applicableAttributes,
        boolean active,
        int displayOrder
) {}

