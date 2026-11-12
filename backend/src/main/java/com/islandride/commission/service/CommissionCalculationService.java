package com.islandride.commission.service;

import com.islandride.booking.domain.Booking;
import com.islandride.commission.domain.Commission;
import com.islandride.commission.domain.CommissionStatus;
import com.islandride.commission.repository.CommissionRepository;
import com.islandride.paymentfinance.domain.PartnerReceivable;
import com.islandride.paymentfinance.domain.ReceivableStatus;
import com.islandride.paymentfinance.repository.PartnerReceivableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CommissionCalculationService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final CommissionRepository commissionRepository;
    private final PartnerReceivableRepository receivableRepository;

    public CommissionCalculationService(CommissionRepository commissionRepository,
                                        PartnerReceivableRepository receivableRepository) {
        this.commissionRepository = commissionRepository;
        this.receivableRepository = receivableRepository;
    }

    /**
     * Calculates platform commission strictly on the rental + fee base excluding GST and security deposit.
     * Records a {@link PartnerReceivable} when online platform-held collections are insufficient to cover
     * platform commission (e.g., when offline pickup collection exceeds the net balance due).
     */
    @Transactional
    public Commission calculateAndRecord(Booking booking,
                                           BigDecimal onlineRentalCollected,
                                           BigDecimal offlineRentalCollected) {
        if (booking == null) {
            throw new IllegalArgumentException("Booking is required for commission calculation.");
        }
        if (onlineRentalCollected != null && onlineRentalCollected.signum() < 0) {
            throw new IllegalArgumentException("Online rental collected amount cannot be negative.");
        }
        if (offlineRentalCollected != null && offlineRentalCollected.signum() < 0) {
            throw new IllegalArgumentException("Offline rental collected amount cannot be negative.");
        }

        BigDecimal totalPayable = nonNegative(booking.getTotalPayableAmount());
        BigDecimal taxExcluded = nonNegative(booking.getTaxAmount());
        BigDecimal depositExcluded = nonNegative(booking.getSecurityDepositAmount());
        BigDecimal commissionableBase = totalPayable.subtract(taxExcluded).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        if (booking.getBaseRentalAmount() != null) {
            BigDecimal componentRentalBase = nonNegative(booking.getBaseRentalAmount())
                    .add(booking.getSeasonalAdjustmentAmount() != null ? booking.getSeasonalAdjustmentAmount() : BigDecimal.ZERO)
                    .add(nonNegative(booking.getSurchargeAmount()))
                    .subtract(nonNegative(booking.getDiscountAmount()))
                    .max(BigDecimal.ZERO)
                    .add(nonNegative(booking.getCleaningFee()))
                    .add(nonNegative(booking.getDeliveryFee()))
                    .setScale(2, RoundingMode.HALF_UP);
            if (depositExcluded.signum() > 0 && commissionableBase.subtract(componentRentalBase).compareTo(depositExcluded) >= 0) {
                throw new IllegalStateException(
                        "Security deposit must be isolated and excluded from booking totalPayableAmount and commission base for booking "
                                + booking.getPublicId()
                );
            }
        }

        BigDecimal rate = booking.getCommissionRateSnapshot();
        if (rate == null || rate.signum() < 0) {
            throw new IllegalStateException(
                    "Booking " + booking.getPublicId() + " is missing a valid commissionRateSnapshot."
            );
        }
        BigDecimal commissionAmount = commissionableBase.multiply(rate)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

        BigDecimal onlineCollected = nonNegative(onlineRentalCollected);
        BigDecimal offlineCollected = nonNegative(offlineRentalCollected);
        if (depositExcluded.signum() > 0 && onlineCollected.add(offlineCollected).subtract(totalPayable).compareTo(depositExcluded) >= 0) {
            throw new IllegalArgumentException(
                    "Collected rental amounts for commission calculation must exclude security deposit for booking "
                            + booking.getPublicId()
            );
        }

        BigDecimal partnerPayout;
        BigDecimal partnerReceivable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (onlineCollected.compareTo(commissionAmount) >= 0) {
            partnerPayout = onlineCollected.subtract(commissionAmount).setScale(2, RoundingMode.HALF_UP);
        } else {
            partnerPayout = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            partnerReceivable = commissionAmount.subtract(onlineCollected).setScale(2, RoundingMode.HALF_UP);
        }

        Commission commission = commissionRepository.findByBookingId(booking.getId())
                .orElseGet(Commission::new);
        commission.setBooking(booking);
        commission.setBusiness(booking.getBusiness());
        commission.setGrossBookingAmount(totalPayable);
        commission.setTaxExcludedAmount(taxExcluded);
        commission.setDepositExcludedAmount(depositExcluded);
        commission.setCommissionPercentage(rate);
        commission.setCommissionAmount(commissionAmount);
        commission.setPartnerPayoutAmount(partnerPayout);
        commission.setOnlineCollectedAmount(onlineCollected);
        commission.setOfflineCollectedAmount(offlineCollected);
        commission.setPartnerReceivableAmount(partnerReceivable);
        commission.setStatus(CommissionStatus.ACCRUED);

        Commission saved = commissionRepository.save(commission);

        if (partnerReceivable.signum() > 0) {
            PartnerReceivable receivable = receivableRepository.findByBookingId(booking.getId())
                    .orElseGet(PartnerReceivable::new);
            receivable.setBusiness(booking.getBusiness());
            receivable.setBooking(booking);
            receivable.setCommission(saved);
            receivable.setAmountDueFromPartner(partnerReceivable);
            receivable.setCurrency(booking.getCurrency());
            receivable.setStatus(ReceivableStatus.OPEN);
            receivable.setNotes("Commission shortfall offset due to offline collection exceeding online rental hold.");
            receivableRepository.save(receivable);
        }

        return saved;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}

