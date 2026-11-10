package com.islandride.usermanagement.dto;

import java.time.LocalDate;

public record TouristProfileDto(
        String drivingLicenseNumber,
        LocalDate drivingLicenseExpiry,
        String emergencyContactName,
        String emergencyContactPhone,
        String nationality,
        String verificationStatus
) {}

