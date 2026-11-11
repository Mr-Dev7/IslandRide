package com.islandride.inventory.dto;

public record PartnerInventorySummaryDto(
        String businessPublicId,
        String businessTradeName,
        String businessVerificationStatus,
        long totalVehicles,
        long activeVehicles,
        long pendingReviewVehicles,
        long draftVehicles,
        long pausedOrMaintenanceVehicles,
        long rejectedOrSuspendedVehicles,
        long archivedVehicles,
        long expiringDocumentsCount
) {}

