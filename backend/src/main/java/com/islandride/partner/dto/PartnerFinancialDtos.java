package com.islandride.partner.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PartnerFinancialDtos {

    private PartnerFinancialDtos() {
    }

    public record PartnerFinancialOverviewDto(
            Long businessId,
            String tradeName,
            BigDecimal totalGrossBookings,
            BigDecimal totalCommissionDeducted,
            BigDecimal totalNetEarnings,
            BigDecimal onlineCollectedByPlatform,
            BigDecimal offlineCollectedByPartner,
            BigDecimal totalSettledPaidOut,
            BigDecimal pendingSettlementBalance,
            BigDecimal openReceivablesBalance,
            int totalCompletedBookings,
            List<CommissionDetailDto> recentCommissions,
            List<SettlementDetailDto> recentSettlements
    ) {
    }

    public record CommissionDetailDto(
            Long commissionId,
            String bookingPublicId,
            String bookingReference,
            String vehicleTitle,
            BigDecimal grossRentalAmount,
            BigDecimal commissionPercentage,
            BigDecimal commissionAmount,
            BigDecimal partnerEarnings,
            String status,
            Instant createdAt
    ) {
    }

    public record SettlementDetailDto(
            String publicId,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal grossAmount,
            BigDecimal commissionDeducted,
            BigDecimal netPayoutAmount,
            String bankUtrReference,
            String status,
            Instant processedAt,
            Instant createdAt
    ) {
    }
}

