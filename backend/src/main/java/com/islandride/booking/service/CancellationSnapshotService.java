package com.islandride.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.islandride.booking.domain.DisruptionExceptionType;
import com.islandride.booking.domain.GatewayFeeTreatment;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.pricing.domain.PartnerCancellationTier;
import com.islandride.pricing.repository.PartnerCancellationTierRepository;
import com.islandride.pricing.service.BookingPolicyService;
import com.islandride.pricing.service.EffectiveBookingPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class CancellationSnapshotService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final BookingPolicyService bookingPolicyService;
    private final PartnerCancellationTierRepository tierRepository;
    private final ObjectMapper objectMapper;

    public CancellationSnapshotService(BookingPolicyService bookingPolicyService,
                                       PartnerCancellationTierRepository tierRepository) {
        this.bookingPolicyService = bookingPolicyService;
        this.tierRepository = tierRepository;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Transactional(readOnly = true)
    public CancellationPolicySnapshot captureSnapshot(Long businessId,
                                                      BigDecimal securityDepositAmount,
                                                      Instant capturedAt) {
        Instant effectiveNow = capturedAt != null ? capturedAt : Instant.now();
        EffectiveBookingPolicy policy = bookingPolicyService.resolvePolicy(businessId);

        List<CancellationPolicySnapshot.CancellationTierSnapshot> tierSnapshots = tierRepository
                .findByBusinessIdAndActiveTrueOrderByPriorityAscMinHoursBeforePickupDesc(businessId)
                .stream()
                .map(this::toTierSnapshot)
                .toList();

        return new CancellationPolicySnapshot(
                effectiveNow,
                policy.currency(),
                policy.cancellationDeadlineHours(),
                policy.freeCancellationAllowed(),
                policy.cancellationFeePercentage(),
                tierSnapshots,
                policy.noShowGraceMinutes(),
                policy.noShowPenaltyPercentage(),
                policy.gatewayFeeTreatment(),
                policy.reschedulingAllowedForDisruption(),
                policy.depositCollectionMethod(),
                securityDepositAmount != null ? securityDepositAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        );
    }

    public String serialize(CancellationPolicySnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize cancellation policy snapshot", ex);
        }
    }

    public CancellationPolicySnapshot deserialize(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, CancellationPolicySnapshot.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse cancellation policy snapshot", ex);
        }
    }

    public CancellationSettlementBreakdown evaluateCancellation(CancellationPolicySnapshot snapshot,
                                                              Instant pickupStart,
                                                              Instant cancelAt,
                                                              BigDecimal rentalTotalWithTax,
                                                              BigDecimal rentalOnlinePaid,
                                                              BigDecimal depositOnlinePaid,
                                                              BigDecimal estimatedGatewayFee) {
        Instant effectiveCancelAt = cancelAt != null ? cancelAt : Instant.now();
        BigDecimal totalForFeeBase = nonNegative(rentalTotalWithTax);
        BigDecimal rentalPaid = nonNegative(rentalOnlinePaid);
        BigDecimal depositPaid = nonNegative(depositOnlinePaid);
        long minutesBeforePickup = Math.max(0L, ChronoUnit.MINUTES.between(effectiveCancelAt, pickupStart));

        BigDecimal penalty = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String appliedRule;

        CancellationPolicySnapshot.CancellationTierSnapshot matchingTier = findMatchingTier(snapshot, minutesBeforePickup);
        if (matchingTier != null) {
            if (matchingTier.flatFeeAmount() != null) {
                penalty = nonNegative(matchingTier.flatFeeAmount());
                appliedRule = "TIER_FLAT_" + matchingTier.minHoursBeforePickup() + "H";
            } else {
                penalty = percentageOf(totalForFeeBase, matchingTier.feePercentage());
                appliedRule = "TIER_PERCENT_" + matchingTier.feePercentage() + "_" + matchingTier.minHoursBeforePickup() + "H";
            }
        } else if (snapshot.freeCancellationAllowed()
                && minutesBeforePickup >= snapshot.cancellationDeadlineHours() * 60L) {
            penalty = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            appliedRule = "FREE_CANCELLATION_BEFORE_" + snapshot.cancellationDeadlineHours() + "H";
        } else {
            penalty = percentageOf(totalForFeeBase, snapshot.defaultFeePercentage());
            appliedRule = "DEFAULT_FEE_" + snapshot.defaultFeePercentage() + "_PERCENT";
        }

        if (penalty.compareTo(rentalPaid) > 0) {
            penalty = rentalPaid;
        }

        BigDecimal rentalRefundBeforeFee = rentalPaid.subtract(penalty).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gatewayFeeDeducted = computeGatewayFeeDeduction(
                snapshot.gatewayFeeTreatment(),
                rentalRefundBeforeFee,
                estimatedGatewayFee
        );
        BigDecimal netRentalRefund = rentalRefundBeforeFee.subtract(gatewayFeeDeducted)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        // Security deposit is always 100% protected on rental cancellation
        BigDecimal depositRefund = depositPaid.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRefund = netRentalRefund.add(depositRefund).setScale(2, RoundingMode.HALF_UP);

        return new CancellationSettlementBreakdown(
                penalty,
                rentalRefundBeforeFee,
                gatewayFeeDeducted,
                netRentalRefund,
                depositRefund,
                totalRefund,
                true,
                false,
                null,
                appliedRule
        );
    }

    public CancellationSettlementBreakdown evaluateDisruptionOverride(CancellationPolicySnapshot snapshot,
                                                                    DisruptionExceptionType disruptionType,
                                                                    BigDecimal rentalOnlinePaid,
                                                                    BigDecimal depositOnlinePaid,
                                                                    boolean preferReschedule) {
        if (disruptionType == null) {
            throw new BusinessRuleException("Disruption exception type is required.");
        }
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal rentalRefund = nonNegative(rentalOnlinePaid).setScale(2, RoundingMode.HALF_UP);
        BigDecimal depositRefund = nonNegative(depositOnlinePaid).setScale(2, RoundingMode.HALF_UP);
        boolean rescheduling = preferReschedule && snapshot.reschedulingAllowedForDisruption();

        return new CancellationSettlementBreakdown(
                zero,
                rentalRefund,
                zero,
                rentalRefund,
                depositRefund,
                rentalRefund.add(depositRefund).setScale(2, RoundingMode.HALF_UP),
                true,
                rescheduling,
                disruptionType,
                "DISRUPTION_OVERRIDE_" + disruptionType.name()
        );
    }

    public CancellationSettlementBreakdown evaluateNoShow(CancellationPolicySnapshot snapshot,
                                                        Instant pickupStart,
                                                        Instant markedAt,
                                                        String evidenceNotes,
                                                        String evidenceRef,
                                                        BigDecimal rentalOnlinePaid,
                                                        BigDecimal depositOnlinePaid) {
        Instant effectiveMarkAt = markedAt != null ? markedAt : Instant.now();
        Instant graceEndsAt = pickupStart.plus(snapshot.noShowGraceMinutes(), ChronoUnit.MINUTES);
        if (effectiveMarkAt.isBefore(graceEndsAt)) {
            throw new BusinessRuleException(
                    "Cannot mark NO_SHOW before the grace window expires at " + graceEndsAt + "."
            );
        }
        boolean hasNotes = evidenceNotes != null && !evidenceNotes.isBlank();
        boolean hasRef = evidenceRef != null && !evidenceRef.isBlank();
        if (!hasNotes && !hasRef) {
            throw new BusinessRuleException("NO_SHOW requires supporting evidence notes or evidence document reference.");
        }

        BigDecimal rentalPaid = nonNegative(rentalOnlinePaid);
        BigDecimal depositPaid = nonNegative(depositOnlinePaid);
        BigDecimal penalty = percentageOf(rentalPaid, snapshot.noShowPenaltyPercentage());
        if (penalty.compareTo(rentalPaid) > 0) {
            penalty = rentalPaid;
        }
        BigDecimal netRentalRefund = rentalPaid.subtract(penalty).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        // Deposit does not get forfeited by a pre-handover NO_SHOW
        BigDecimal depositRefund = depositPaid.setScale(2, RoundingMode.HALF_UP);
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return new CancellationSettlementBreakdown(
                penalty,
                netRentalRefund,
                zero,
                netRentalRefund,
                depositRefund,
                netRentalRefund.add(depositRefund).setScale(2, RoundingMode.HALF_UP),
                true,
                false,
                null,
                "NO_SHOW_PENALTY_" + snapshot.noShowPenaltyPercentage() + "_PERCENT"
        );
    }

    private CancellationPolicySnapshot.CancellationTierSnapshot toTierSnapshot(PartnerCancellationTier tier) {
        if (tier.isAppliesToDeposit()) {
            throw new BusinessRuleException(
                    "Security deposits are strictly isolated and cannot be forfeited by cancellation tiers."
            );
        }
        return new CancellationPolicySnapshot.CancellationTierSnapshot(
                tier.getMinHoursBeforePickup(),
                tier.getMaxHoursBeforePickup(),
                tier.getFeePercentage(),
                tier.getFlatFeeAmount(),
                false,
                tier.getPriority()
        );
    }

    private CancellationPolicySnapshot.CancellationTierSnapshot findMatchingTier(CancellationPolicySnapshot snapshot,
                                                                                   long minutesBeforePickup) {
        if (snapshot.tiers() == null || snapshot.tiers().isEmpty()) {
            return null;
        }
        for (CancellationPolicySnapshot.CancellationTierSnapshot tier : snapshot.tiers()) {
            long minMinutes = Math.max(0L, tier.minHoursBeforePickup()) * 60L;
            boolean aboveMin = minutesBeforePickup >= minMinutes;
            boolean belowMax = tier.maxHoursBeforePickup() == null
                    || minutesBeforePickup < Math.max(0L, tier.maxHoursBeforePickup()) * 60L;
            if (aboveMin && belowMax) {
                return tier;
            }
        }
        return null;
    }

    private BigDecimal computeGatewayFeeDeduction(GatewayFeeTreatment treatment,
                                                    BigDecimal refundableRental,
                                                    BigDecimal estimatedGatewayFee) {
        if (treatment != GatewayFeeTreatment.DEDUCTED_FROM_REFUND
                || refundableRental.signum() <= 0
                || estimatedGatewayFee == null
                || estimatedGatewayFee.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return refundableRental.min(estimatedGatewayFee).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentageOf(BigDecimal amount, BigDecimal percentage) {
        if (amount == null || percentage == null || amount.signum() <= 0 || percentage.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.multiply(percentage)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value;
    }
}

