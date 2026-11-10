package com.islandride.partner.dto;

import java.math.BigDecimal;

public record BusinessDto(
        String publicId,
        String legalName,
        String tradeName,
        String gstin,
        String panNumber,
        String verificationStatus,
        String rejectionReason,
        BigDecimal commissionRate
) {}

