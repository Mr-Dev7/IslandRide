package com.islandride.identityaccess.dto;

import java.util.List;

public record UserDto(
        String publicId,
        String email,
        String firstName,
        String lastName,
        String phone,
        String status,
        List<String> roles,
        Long businessId
) {}
