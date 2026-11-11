package com.islandride.inventory.dto;

import com.islandride.inventory.domain.DocumentVerificationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VerifyVehicleDocumentRequest(
        @NotNull(message = "Verification status is required")
        DocumentVerificationStatus status,

        @Size(max = 1000)
        String rejectionReason
) {}

