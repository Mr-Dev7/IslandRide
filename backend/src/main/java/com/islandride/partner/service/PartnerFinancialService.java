package com.islandride.partner.service;

import com.islandride.booking.domain.Booking;
import com.islandride.commission.domain.Commission;
import com.islandride.commission.domain.Settlement;
import com.islandride.commission.domain.SettlementStatus;
import com.islandride.commission.repository.CommissionRepository;
import com.islandride.commission.repository.SettlementRepository;
import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.core.security.TenantSecurityService;
import com.islandride.partner.domain.Business;
import com.islandride.partner.dto.PartnerFinancialDtos;
import com.islandride.partner.dto.PartnerFinancialDtos.CommissionDetailDto;
import com.islandride.partner.dto.PartnerFinancialDtos.PartnerFinancialOverviewDto;
import com.islandride.partner.dto.PartnerFinancialDtos.SettlementDetailDto;
import com.islandride.partner.repository.BusinessRepository;
import com.islandride.paymentfinance.domain.PartnerReceivable;
import com.islandride.paymentfinance.domain.ReceivableStatus;
import com.islandride.paymentfinance.repository.PartnerReceivableRepository;
import com.islandride.pricing.service.BookingPolicyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Service
public class PartnerFinancialService {

    private final CommissionRepository commissionRepository;
    private final SettlementRepository settlementRepository;
    private final PartnerReceivableRepository receivableRepository;
    private final BusinessRepository businessRepository;
    private final TenantSecurityService tenantSecurityService;
    private final BookingPolicyService bookingPolicyService;

    @Autowired
    public PartnerFinancialService(CommissionRepository commissionRepository,
                                   SettlementRepository settlementRepository,
                                   PartnerReceivableRepository receivableRepository,
                                   BusinessRepository businessRepository,
                                   TenantSecurityService tenantSecurityService,
                                   @Autowired(required = false) BookingPolicyService bookingPolicyService) {
        this.commissionRepository = commissionRepository;
        this.settlementRepository = settlementRepository;
        this.receivableRepository = receivableRepository;
        this.businessRepository = businessRepository;
        this.tenantSecurityService = tenantSecurityService;
        this.bookingPolicyService = bookingPolicyService;
    }

    @Transactional(readOnly = true)
    public PartnerFinancialOverviewDto getFinancialOverview(Long businessId) {
        tenantSecurityService.requireBusinessReadAccess(businessId);
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + businessId));

        List<Commission> commissions = commissionRepository.findByBusinessId(businessId);
        List<Settlement> settlements = settlementRepository.findByBusinessIdOrderByCreatedAtDesc(businessId);
        List<PartnerReceivable> openReceivables = receivableRepository.findByBusinessIdAndStatus(businessId, ReceivableStatus.OPEN);

        BigDecimal totalGross = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCommission = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalNetEarnings = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalOnlineCollected = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalOfflineCollected = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (Commission c : commissions) {
            if (c.getGrossBookingAmount() != null) totalGross = totalGross.add(c.getGrossBookingAmount());
            if (c.getCommissionAmount() != null) totalCommission = totalCommission.add(c.getCommissionAmount());
            if (c.getPartnerPayoutAmount() != null) totalNetEarnings = totalNetEarnings.add(c.getPartnerPayoutAmount());
            if (c.getOnlineCollectedAmount() != null) totalOnlineCollected = totalOnlineCollected.add(c.getOnlineCollectedAmount());
            if (c.getOfflineCollectedAmount() != null) totalOfflineCollected = totalOfflineCollected.add(c.getOfflineCollectedAmount());
        }

        BigDecimal totalSettledPaidOut = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (Settlement s : settlements) {
            if (s.getStatus() == SettlementStatus.COMPLETED && s.getNetPayoutAmount() != null) {
                totalSettledPaidOut = totalSettledPaidOut.add(s.getNetPayoutAmount());
            }
        }

        BigDecimal pendingSettlementBalance = totalNetEarnings.subtract(totalSettledPaidOut).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        BigDecimal openReceivablesBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (PartnerReceivable r : openReceivables) {
            BigDecimal due = r.getAmountDueFromPartner() != null ? r.getAmountDueFromPartner() : BigDecimal.ZERO;
            BigDecimal settled = r.getAmountSettled() != null ? r.getAmountSettled() : BigDecimal.ZERO;
            openReceivablesBalance = openReceivablesBalance.add(due.subtract(settled).max(BigDecimal.ZERO));
        }

        List<CommissionDetailDto> recentCommissions = commissions.stream()
                .sorted(Comparator.comparing(Commission::getCreatedAt).reversed())
                .limit(20)
                .map(this::toCommissionDetailDto)
                .toList();

        List<SettlementDetailDto> recentSettlements = settlements.stream()
                .limit(20)
                .map(this::toSettlementDetailDto)
                .toList();

        int cycleDays = (bookingPolicyService != null)
                ? bookingPolicyService.resolveSettlementCycleDays()
                : 7;
        boolean autoOffset = (bookingPolicyService != null)
                ? bookingPolicyService.resolveSettlementAutoOffsetReceivables()
                : true;

        return new PartnerFinancialOverviewDto(
                business.getId(),
                business.getTradeName(),
                totalGross,
                totalCommission,
                totalNetEarnings,
                totalOnlineCollected,
                totalOfflineCollected,
                totalSettledPaidOut,
                pendingSettlementBalance,
                openReceivablesBalance,
                commissions.size(),
                cycleDays,
                autoOffset,
                recentCommissions,
                recentSettlements
        );
    }

    @Transactional(readOnly = true)
    public List<CommissionDetailDto> getCommissions(Long businessId) {
        tenantSecurityService.requireBusinessReadAccess(businessId);
        return commissionRepository.findByBusinessId(businessId).stream()
                .sorted(Comparator.comparing(Commission::getCreatedAt).reversed())
                .map(this::toCommissionDetailDto)
                .toList();
    }

    private CommissionDetailDto toCommissionDetailDto(Commission c) {
        Booking b = c.getBooking();
        String bookingPubId = b != null ? b.getPublicId() : null;
        String bookingRef = b != null ? b.getBookingReference() : null;
        String vehicleTitle = (b != null && b.getVehicle() != null) ? b.getVehicle().getTitle() : null;

        return new CommissionDetailDto(
                c.getId(),
                bookingPubId,
                bookingRef,
                vehicleTitle,
                c.getGrossBookingAmount(),
                c.getCommissionPercentage(),
                c.getCommissionAmount(),
                c.getPartnerPayoutAmount(),
                c.getStatus() != null ? c.getStatus().name() : "ACCRUED",
                c.getCreatedAt()
        );
    }

    private SettlementDetailDto toSettlementDetailDto(Settlement s) {
        return new SettlementDetailDto(
                s.getPublicId(),
                s.getSettlementPeriodStart(),
                s.getSettlementPeriodEnd(),
                s.getTotalGrossAmount(),
                s.getTotalCommissionDeducted(),
                s.getNetPayoutAmount(),
                s.getBankUtrReference(),
                s.getStatus() != null ? s.getStatus().name() : "INITIATED",
                s.getProcessedAt(),
                s.getCreatedAt()
        );
    }
}
