package com.islandride.geography.dto;

import java.math.BigDecimal;

public record LocationDto(
        Long id,
        String publicId,
        Long islandId,
        String islandName,
        String islandCode,
        String name,
        String code,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String contactPhone,
        boolean active
) {}

