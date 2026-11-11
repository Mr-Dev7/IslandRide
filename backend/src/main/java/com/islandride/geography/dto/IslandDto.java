package com.islandride.geography.dto;

public record IslandDto(
        Long id,
        String name,
        String displayAlias,
        String code,
        String description,
        int displayOrder,
        boolean active
) {}

