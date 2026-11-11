package com.islandride.pricing.service;

import com.islandride.geography.domain.Location;

public record OperatingHoursValidationResult(
        Location resolvedPickupLocation,
        Location resolvedReturnLocation
) {
}

