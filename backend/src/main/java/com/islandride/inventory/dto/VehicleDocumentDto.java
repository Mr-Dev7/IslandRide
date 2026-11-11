package com.islandride.inventory.dto;

import java.time.Instant;
import java.time.LocalDate;

public record VehicleDocumentDto(
        String publicId,
        String vehiclePublicId,
        String documentTypeCode,
        String documentTypeName,
        String documentNumber,
        String downloadUrl,
        String originalFilename,
        String contentType,
        Long fileSizeBytes,
        LocalDate issueDate,
        LocalDate expiryDate,
        boolean expired,
        boolean expiringSoon,
        String verificationStatus,
        String rejectionReason,
        Instant verifiedAt,
        Instant createdAt
) {}

