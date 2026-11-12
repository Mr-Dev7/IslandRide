package com.islandride.pricing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.islandride.booking.domain.PaymentMode;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.pricing.domain.PricingRule;
import com.islandride.pricing.domain.SeasonalPrice;
import com.islandride.pricing.domain.VehiclePricingPlan;
import com.islandride.pricing.repository.PricingRuleRepository;
import com.islandride.pricing.repository.SeasonalPriceRepository;
import com.islandride.pricing.repository.VehiclePricingPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class AuthoritativePricingService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ONE = new BigDecimal("1.00");

    private final VehiclePricingPlanRepository pricingPlanRepository;
    private final SeasonalPriceRepository seasonalPriceRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final BookingPolicyService bookingPolicyService;
    private final VehicleRepository vehicleRepository;
    private final ObjectMapper objectMapper;

    public AuthoritativePricingService(VehiclePricingPlanRepository pricingPlanRepository,
                                       SeasonalPriceRepository seasonalPriceRepository,
                                       PricingRuleRepository pricingRuleRepository,
                                       BookingPolicyService bookingPolicyService,
                                       VehicleRepository vehicleRepository) {
        this.pricingPlanRepository = pricingPlanRepository;
        this.seasonalPriceRepository = seasonalPriceRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.bookingPolicyService = bookingPolicyService;
        this.vehicleRepository = vehicleRepository;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Transactional(readOnly = true)
    public PricingSnapshot calculateQuote(Vehicle requestedVehicle,
                                        Instant startTime,
                                        Instant endTime,
                                        PaymentMode requestedPaymentMode,
                                        boolean deliveryRequested,
                                        Instant quoteAt) {
        if (requestedVehicle == null) {
            throw new BusinessRuleException("Vehicle is required to compute a pricing quote.");
        }
        Vehicle vehicle = requestedVehicle.getId() != null
                ? vehicleRepository.findById(requestedVehicle.getId()).orElse(requestedVehicle)
                : requestedVehicle;
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new BusinessRuleException("Rental start time must be strictly before end time.");
        }
        Instant effectiveQuoteAt = quoteAt != null ? quoteAt : Instant.now();
        EffectiveBookingPolicy policy = bookingPolicyService.resolvePolicy(vehicle);

        long leadMinutes = Duration.between(effectiveQuoteAt, startTime).toMinutes();
        if (leadMinutes < policy.minLeadTimeMinutes()) {
            throw new BusinessRuleException(
                    "Booking start must be at least " + policy.minLeadTimeMinutes() + " minutes in the future."
            );
        }

        long durationMinutes = Duration.between(startTime, endTime).toMinutes();
        VehiclePricingPlan activePlan = resolveEffectivePlan(vehicle, startTime);

        int minRentalHours = activePlan != null ? activePlan.getMinRentalHours() : policy.minRentalHours();
        int maxRentalDays = activePlan != null ? activePlan.getMaxRentalDays() : policy.maxRentalDays();

        if (durationMinutes < minRentalHours * 60L) {
            throw new BusinessRuleException("Minimum rental duration is " + minRentalHours + " hours.");
        }

        int billableDays = (int) Math.max(1L, (durationMinutes + 1439L) / 1440L);
        if (billableDays > maxRentalDays) {
            throw new BusinessRuleException("Maximum rental duration allowed is " + maxRentalDays + " days.");
        }

        String currency = activePlan != null ? activePlan.getCurrency() : policy.currency();
        BigDecimal hourlyRate = activePlan != null ? activePlan.getHourlyRate() : null;
        BigDecimal dailyRate = activePlan != null ? activePlan.getDailyRate() : vehicle.getDailyRate();
        BigDecimal weeklyRate = activePlan != null ? activePlan.getWeeklyRate() : null;
        BigDecimal securityDeposit = (activePlan != null ? activePlan.getSecurityDeposit() : vehicle.getSecurityDeposit())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal cleaningFee = activePlan != null && activePlan.getCleaningFee() != null
                ? activePlan.getCleaningFee().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal deliveryFee = deliveryRequested && activePlan != null && activePlan.getDeliveryFee() != null
                ? activePlan.getDeliveryFee().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        String billingUnit;
        Integer billableHours = null;
        int billableWeeks = 0;
        int remainderDays = billableDays;
        BigDecimal baseRentalAmount;

        if (durationMinutes < 1440L && hourlyRate != null && hourlyRate.signum() > 0) {
            int exactHours = (int) ((durationMinutes + 59L) / 60L);
            billingUnit = "HOURLY";
            billableHours = exactHours;
            billableDays = 1;
            remainderDays = 1;
            baseRentalAmount = hourlyRate.multiply(BigDecimal.valueOf(exactHours)).setScale(2, RoundingMode.HALF_UP);
        } else if (weeklyRate != null && weeklyRate.signum() > 0 && billableDays >= 7) {
            billableWeeks = billableDays / 7;
            remainderDays = billableDays % 7;
            BigDecimal weeklyPlusRemainder = weeklyRate.multiply(BigDecimal.valueOf(billableWeeks))
                    .add(dailyRate.multiply(BigDecimal.valueOf(remainderDays)))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal pureDaily = dailyRate.multiply(BigDecimal.valueOf(billableDays)).setScale(2, RoundingMode.HALF_UP);
            if (weeklyPlusRemainder.compareTo(pureDaily) <= 0) {
                billingUnit = remainderDays == 0 ? "WEEKLY" : "WEEKLY_PLUS_DAILY";
                baseRentalAmount = weeklyPlusRemainder;
            } else {
                billingUnit = "DAILY";
                billableWeeks = 0;
                remainderDays = billableDays;
                baseRentalAmount = pureDaily;
            }
        } else {
            billingUnit = "DAILY";
            baseRentalAmount = dailyRate.multiply(BigDecimal.valueOf(billableDays)).setScale(2, RoundingMode.HALF_UP);
        }

        ZoneId zoneId = policy.operatingHoursTimezone();
        LocalDate startDate = startTime.atZone(zoneId).toLocalDate();
        LocalDate lastRentalDate = startDate.plusDays(Math.max(0, billableDays - 1));
        LocalDate endInstantDate = endTime.minusNanos(1).atZone(zoneId).toLocalDate();
        LocalDate effectiveRuleEndDate = endInstantDate.isAfter(lastRentalDate) ? endInstantDate : lastRentalDate;
        int seasonalDayCount = (durationMinutes < 1440L && endInstantDate.isAfter(startDate))
                ? (int) Math.max(1L, java.time.temporal.ChronoUnit.DAYS.between(startDate, endInstantDate) + 1L)
                : billableDays;

        SeasonalComputation seasonResult = computeSeasonalAdjustment(
                vehicle,
                startDate,
                seasonalDayCount,
                baseRentalAmount
        );
        BigDecimal seasonalAdjustedBase = baseRentalAmount.add(seasonResult.adjustmentAmount())
                .setScale(2, RoundingMode.HALF_UP);

        RuleEvaluationResult ruleResult = evaluatePricingRules(
                vehicle,
                billableDays,
                startDate,
                effectiveRuleEndDate,
                seasonalAdjustedBase
        );

        BigDecimal adjustedRentalSubtotal = seasonalAdjustedBase
                .add(ruleResult.surchargeAmount())
                .subtract(ruleResult.discountAmount())
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        // Commission base excludes GST and security deposit (Decision #2 & #7)
        BigDecimal commissionableRentalBase = adjustedRentalSubtotal
                .add(cleaningFee)
                .add(deliveryFee)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal gstPercentage = policy.gstPercentage().setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = commissionableRentalBase.multiply(gstPercentage)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

        // Total rental payable excludes security deposit (kept separate per Decision #2)
        BigDecimal totalPayableAmount = commissionableRentalBase.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        PaymentMode requestedMode = requestedPaymentMode != null ? requestedPaymentMode : PaymentMode.FULL;
        boolean forceFull = Duration.between(effectiveQuoteAt, startTime).toMinutes() < policy.fullPaymentCutoffHours() * 60L;
        PaymentMode enforcedMode = (forceFull || requestedMode == PaymentMode.FULL)
                ? PaymentMode.FULL
                : PaymentMode.PARTIAL_ADVANCE;

        BigDecimal advancePct = enforcedMode == PaymentMode.FULL
                ? ONE_HUNDRED.setScale(2, RoundingMode.HALF_UP)
                : policy.advancePaymentPercentage().setScale(2, RoundingMode.HALF_UP);

        BigDecimal advanceDueNow = enforcedMode == PaymentMode.FULL
                ? totalPayableAmount
                : totalPayableAmount.multiply(advancePct).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

        BigDecimal pickupBalanceDue = totalPayableAmount.subtract(advanceDueNow)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal commissionRate = policy.commissionPercentage().setScale(2, RoundingMode.HALF_UP);
        BigDecimal estimatedCommission = commissionableRentalBase.multiply(commissionRate)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

        return new PricingSnapshot(
                effectiveQuoteAt,
                vehicle.getPublicId(),
                activePlan != null ? activePlan.getPublicId() : null,
                currency,
                startTime,
                endTime,
                durationMinutes,
                billingUnit,
                billableDays,
                billableHours,
                billableWeeks,
                remainderDays,
                hourlyRate,
                dailyRate.setScale(2, RoundingMode.HALF_UP),
                weeklyRate,
                baseRentalAmount,
                seasonResult.averageMultiplier(),
                seasonResult.adjustmentAmount(),
                seasonResult.dailyBreakdowns(),
                ruleResult.appliedBreakdowns(),
                ruleResult.discountAmount(),
                ruleResult.surchargeAmount(),
                adjustedRentalSubtotal,
                cleaningFee,
                deliveryFee,
                commissionableRentalBase,
                gstPercentage,
                taxAmount,
                totalPayableAmount,
                securityDeposit,
                policy.depositCollectionMethod(),
                requestedMode,
                enforcedMode,
                advancePct,
                advanceDueNow,
                pickupBalanceDue,
                commissionRate,
                estimatedCommission,
                policy.turnaroundBufferMinutes(),
                policy.fullPaymentCutoffHours()
        );
    }

    public String serialize(PricingSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize pricing snapshot", ex);
        }
    }

    private VehiclePricingPlan resolveEffectivePlan(Vehicle vehicle, Instant at) {
        return pricingPlanRepository.findByVehicleIdAndActiveTrue(vehicle.getId()).stream()
                .filter(p -> (p.getValidFrom() == null || !p.getValidFrom().isAfter(at))
                        && (p.getValidTo() == null || p.getValidTo().isAfter(at)))
                .max(Comparator.comparing(VehiclePricingPlan::getUpdatedAt))
                .orElse(null);
    }

    private SeasonalComputation computeSeasonalAdjustment(Vehicle vehicle,
                                                            LocalDate startDate,
                                                            int billableDays,
                                                            BigDecimal baseRentalAmount) {
        LocalDate endDate = startDate.plusDays(Math.max(0, billableDays - 1));
        List<SeasonalPrice> seasons = seasonalPriceRepository.findApplicableSeasons(
                        vehicle.getIsland().getId(), startDate, endDate)
                .stream()
                .filter(s -> s.getCategory() == null || s.getCategory().getId().equals(vehicle.getCategory().getId()))
                .toList();

        List<PricingSnapshot.DailySeasonBreakdown> dailyBreakdowns = new ArrayList<>(billableDays);
        BigDecimal multiplierSum = BigDecimal.ZERO;

        for (int i = 0; i < billableDays; i++) {
            LocalDate day = startDate.plusDays(i);
            SeasonalPrice bestMatch = seasons.stream()
                    .filter(s -> !s.getStartDate().isAfter(day) && !s.getEndDate().isBefore(day))
                    .max(Comparator.comparing(SeasonalPrice::getMultiplier))
                    .orElse(null);

            BigDecimal dayMultiplier = bestMatch != null ? bestMatch.getMultiplier() : ONE;
            String seasonName = bestMatch != null ? bestMatch.getSeasonName() : "STANDARD";
            dailyBreakdowns.add(new PricingSnapshot.DailySeasonBreakdown(day.toString(), seasonName, dayMultiplier));
            multiplierSum = multiplierSum.add(dayMultiplier);
        }

        BigDecimal effectiveAvgMultiplier = multiplierSum.divide(BigDecimal.valueOf(billableDays), 2, RoundingMode.HALF_UP);
        BigDecimal seasonalBase = baseRentalAmount.multiply(effectiveAvgMultiplier).setScale(2, RoundingMode.HALF_UP);
        BigDecimal adjustment = seasonalBase.subtract(baseRentalAmount).setScale(2, RoundingMode.HALF_UP);

        return new SeasonalComputation(
                effectiveAvgMultiplier,
                adjustment,
                dailyBreakdowns
        );
    }

    private RuleEvaluationResult evaluatePricingRules(Vehicle vehicle,
                                                      int billableDays,
                                                      LocalDate startDate,
                                                      LocalDate endDate,
                                                      BigDecimal seasonalAdjustedBase) {
        Map<Long, PricingRule> uniqueRules = new LinkedHashMap<>();
        pricingRuleRepository.findByVehicleIdAndActiveTrue(vehicle.getId())
                .forEach(r -> uniqueRules.put(r.getId(), r));
        pricingRuleRepository.findByCategoryIdAndActiveTrue(vehicle.getCategory().getId()).stream()
                .filter(r -> r.getVehicle() == null
                        && (r.getBusiness() == null || r.getBusiness().getId().equals(vehicle.getBusiness().getId())))
                .forEach(r -> uniqueRules.putIfAbsent(r.getId(), r));
        pricingRuleRepository.findByBusinessIdAndActiveTrue(vehicle.getBusiness().getId()).stream()
                .filter(r -> r.getVehicle() == null
                        && (r.getCategory() == null || r.getCategory().getId().equals(vehicle.getCategory().getId())))
                .forEach(r -> uniqueRules.putIfAbsent(r.getId(), r));

        boolean touchesWeekend = rangeTouchesWeekend(startDate, endDate);

        List<PricingRule> candidates = uniqueRules.values().stream()
                .filter(r -> billableDays >= r.getMinDays())
                .filter(r -> !"WEEKEND_SURGE".equalsIgnoreCase(r.getRuleType()) || touchesWeekend)
                .sorted(Comparator.comparingInt(PricingRule::getPriority).thenComparing(PricingRule::getId))
                .toList();

        List<PricingSnapshot.AppliedRuleBreakdown> applied = new ArrayList<>();
        Set<String> usedExclusiveGroups = new HashSet<>();
        boolean nonStackableLock = false;
        BigDecimal totalDiscounts = BigDecimal.ZERO;
        BigDecimal totalSurcharges = BigDecimal.ZERO;

        for (PricingRule rule : candidates) {
            if (nonStackableLock) {
                break;
            }
            if (!rule.isStackable() && !applied.isEmpty()) {
                continue;
            }
            String group = rule.getExclusiveGroup() != null ? rule.getExclusiveGroup().trim().toUpperCase(Locale.ROOT) : "";
            if (!group.isEmpty() && usedExclusiveGroups.contains(group)) {
                continue;
            }

            BigDecimal delta;
            if ("PERCENTAGE".equalsIgnoreCase(rule.getAdjustmentType())) {
                delta = seasonalAdjustedBase.multiply(rule.getAdjustmentValue())
                        .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            } else {
                delta = rule.getAdjustmentValue().setScale(2, RoundingMode.HALF_UP);
            }

            if (delta.signum() < 0) {
                totalDiscounts = totalDiscounts.add(delta.abs());
            } else if (delta.signum() > 0) {
                totalSurcharges = totalSurcharges.add(delta);
            }

            if (!group.isEmpty()) {
                usedExclusiveGroups.add(group);
            }
            if (!rule.isStackable()) {
                nonStackableLock = true;
            }

            applied.add(new PricingSnapshot.AppliedRuleBreakdown(
                    rule.getId(),
                    rule.getRuleName(),
                    rule.getRuleType(),
                    rule.getAdjustmentType(),
                    rule.getAdjustmentValue(),
                    rule.getPriority(),
                    rule.isStackable(),
                    group.isEmpty() ? null : group,
                    delta
            ));
        }

        return new RuleEvaluationResult(
                totalDiscounts.setScale(2, RoundingMode.HALF_UP),
                totalSurcharges.setScale(2, RoundingMode.HALF_UP),
                applied
        );
    }

    private boolean rangeTouchesWeekend(LocalDate startDate, LocalDate endDate) {
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                return true;
            }
            cursor = cursor.plusDays(1);
        }
        return false;
    }

    private record SeasonalComputation(
            BigDecimal averageMultiplier,
            BigDecimal adjustmentAmount,
            List<PricingSnapshot.DailySeasonBreakdown> dailyBreakdowns
    ) {
    }

    private record RuleEvaluationResult(
            BigDecimal discountAmount,
            BigDecimal surchargeAmount,
            List<PricingSnapshot.AppliedRuleBreakdown> appliedBreakdowns
    ) {
    }
}

