package com.islandride.pricing.service;

import com.islandride.administration.domain.SystemSetting;
import com.islandride.administration.repository.SystemSettingRepository;
import com.islandride.administration.service.AuditLogService;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.core.security.TenantSecurityService;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.pricing.domain.PricingRule;
import com.islandride.pricing.domain.VehiclePricingPlan;
import com.islandride.pricing.dto.*;
import com.islandride.pricing.repository.PricingRuleRepository;
import com.islandride.pricing.repository.VehiclePricingPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class VehiclePricingService {

    public static final String SETTING_PLATFORM_CURRENCY = "platform.currency";
    public static final String SETTING_PLATFORM_GST = "platform.gst.percentage";

    private final VehicleRepository vehicleRepository;
    private final VehiclePricingPlanRepository pricingPlanRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final TenantSecurityService tenantSecurityService;
    private final AuditLogService auditLogService;

    public VehiclePricingService(VehicleRepository vehicleRepository,
                                 VehiclePricingPlanRepository pricingPlanRepository,
                                 PricingRuleRepository pricingRuleRepository,
                                 SystemSettingRepository systemSettingRepository,
                                 TenantSecurityService tenantSecurityService,
                                 AuditLogService auditLogService) {
        this.vehicleRepository = vehicleRepository;
        this.pricingPlanRepository = pricingPlanRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.tenantSecurityService = tenantSecurityService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public VehiclePricingOverviewDto getPartnerVehiclePricing(String vehiclePublicId) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        tenantSecurityService.requireBusinessReadAccess(vehicle.getBusiness().getId());
        return buildOverview(vehicle);
    }

    @Transactional(readOnly = true)
    public VehiclePricingOverviewDto buildOverview(Vehicle vehicle) {
        BigDecimal gstPercentage = getPlatformGstPercentage();
        String defaultCurrency = getPlatformCurrency();
        Instant now = Instant.now();

        List<VehiclePricingPlanDto> history = pricingPlanRepository
                .findByVehicleIdOrderByActiveDescCreatedAtDesc(vehicle.getId())
                .stream()
                .map(p -> toDto(p, gstPercentage))
                .toList();

        VehiclePricingPlanDto activePlan = history.stream()
                .filter(p -> p.active() && isEffectiveAt(p, now))
                .findFirst()
                .orElseGet(() -> synthesizeFallbackPlan(vehicle, defaultCurrency, gstPercentage));

        List<PricingRuleDto> rules = pricingRuleRepository.findByVehicleIdAndActiveTrue(vehicle.getId())
                .stream()
                .map(VehiclePricingService::toRuleDto)
                .toList();

        return new VehiclePricingOverviewDto(activePlan, history, rules);
    }

    @Transactional
    public VehiclePricingPlanDto saveVehiclePricing(String vehiclePublicId, SaveVehiclePricingRequest request) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        tenantSecurityService.requireBusinessInventoryWriteAccess(vehicle.getBusiness().getId());

        int minHours = request.minRentalHours() != null ? request.minRentalHours() : 6;
        int maxDays = request.maxRentalDays() != null ? request.maxRentalDays() : 30;
        if (minHours > maxDays * 24) {
            throw new BusinessRuleException("Minimum rental hours (" + minHours + ") cannot exceed maximum rental duration (" + maxDays + " days).");
        }
        if (request.validFrom() != null && request.validTo() != null && !request.validFrom().isBefore(request.validTo())) {
            throw new BusinessRuleException("Pricing validity start timestamp must be strictly before the validity end timestamp.");
        }

        Instant now = Instant.now();
        boolean activatePlan = request.active() == null || request.active();
        if (activatePlan && request.validTo() != null && !request.validTo().isAfter(now)) {
            throw new BusinessRuleException("Active pricing plan validity end timestamp must be in the future.");
        }
        boolean effectiveNow = activatePlan && (request.validFrom() == null || !request.validFrom().isAfter(now));

        String defaultCurrency = getPlatformCurrency();
        String currency = (request.currency() != null && !request.currency().isBlank())
                ? request.currency().trim().toUpperCase(Locale.ROOT)
                : defaultCurrency;

        BigDecimal previousDailyRate = vehicle.getDailyRate();
        BigDecimal previousDeposit = vehicle.getSecurityDeposit();

        if (effectiveNow) {
            List<VehiclePricingPlan> existingActive = pricingPlanRepository.findByVehicleIdAndActiveTrue(vehicle.getId());
            List<VehiclePricingPlan> toDeactivate = existingActive.stream()
                    .filter(p -> p.getValidFrom() == null || !p.getValidFrom().isAfter(now))
                    .toList();
            for (VehiclePricingPlan plan : toDeactivate) {
                plan.setActive(false);
            }
            pricingPlanRepository.saveAll(toDeactivate);
        }

        VehiclePricingPlan plan = new VehiclePricingPlan();
        plan.setVehicle(vehicle);
        plan.setCurrency(currency);
        plan.setHourlyRate(request.hourlyRate());
        plan.setDailyRate(request.dailyRate());
        plan.setWeeklyRate(request.weeklyRate());
        plan.setSecurityDeposit(request.securityDeposit() != null ? request.securityDeposit() : BigDecimal.ZERO);
        plan.setMinRentalHours(minHours);
        plan.setMaxRentalDays(maxDays);
        plan.setIncludedKmPerDay(request.includedKmPerDay());
        plan.setExtraKmCharge(request.extraKmCharge());
        plan.setExtraHourCharge(request.extraHourCharge());
        plan.setCleaningFee(request.cleaningFee() != null ? request.cleaningFee() : BigDecimal.ZERO);
        plan.setDeliveryFee(request.deliveryFee() != null ? request.deliveryFee() : BigDecimal.ZERO);
        plan.setAdditionalChargesDescription(normalizeBlank(request.additionalChargesDescription()));
        plan.setLateReturnFeePerHour(request.lateReturnFeePerHour());
        plan.setLateReturnFlatPenalty(request.lateReturnFlatPenalty());
        plan.setLateReturnGraceMinutes(request.lateReturnGraceMinutes() != null ? request.lateReturnGraceMinutes() : 30);
        plan.setValidFrom(request.validFrom());
        plan.setValidTo(request.validTo());
        plan.setActive(activatePlan);

        VehiclePricingPlan saved = pricingPlanRepository.save(plan);

        if (effectiveNow) {
            vehicle.setDailyRate(saved.getDailyRate());
            vehicle.setSecurityDeposit(saved.getSecurityDeposit());
            vehicleRepository.save(vehicle);
        }

        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("dailyRate", previousDailyRate);
        oldValues.put("securityDeposit", previousDeposit);
        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("planPublicId", saved.getPublicId());
        newValues.put("currency", saved.getCurrency());
        newValues.put("dailyRate", saved.getDailyRate());
        newValues.put("securityDeposit", saved.getSecurityDeposit());
        newValues.put("active", saved.isActive());

        auditLogService.record(
                tenantSecurityService.getCurrentUserIdOrNull(),
                "UPDATE_VEHICLE_PRICING",
                "Vehicle",
                vehicle.getPublicId(),
                oldValues,
                newValues
        );

        return toDto(saved, getPlatformGstPercentage());
    }

    @Transactional
    public PricingRuleDto addPricingRule(String vehiclePublicId, SavePricingRuleRequest request) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        tenantSecurityService.requireBusinessInventoryWriteAccess(vehicle.getBusiness().getId());

        if ("PERCENTAGE".equals(request.adjustmentType())
                && (request.adjustmentValue().compareTo(new BigDecimal("-95.00")) < 0
                    || request.adjustmentValue().compareTo(new BigDecimal("300.00")) > 0)) {
            throw new BusinessRuleException("Percentage adjustment must be between -95.00% and +300.00%.");
        }

        PricingRule rule = new PricingRule();
        rule.setVehicle(vehicle);
        rule.setBusiness(vehicle.getBusiness());
        rule.setCategory(vehicle.getCategory());
        rule.setRuleName(request.ruleName().trim());
        rule.setRuleType(request.ruleType());
        rule.setMinDays(request.minDays());
        rule.setAdjustmentType(request.adjustmentType());
        rule.setAdjustmentValue(request.adjustmentValue());
        rule.setActive(true);

        PricingRule saved = pricingRuleRepository.save(rule);
        auditLogService.record(
                tenantSecurityService.getCurrentUserIdOrNull(),
                "CREATE_PRICING_RULE",
                "PricingRule",
                String.valueOf(saved.getId()),
                null,
                Map.of("vehiclePublicId", vehicle.getPublicId(), "ruleName", saved.getRuleName(), "ruleType", saved.getRuleType())
        );

        return toRuleDto(saved);
    }

    @Transactional
    public void deactivatePricingRule(String vehiclePublicId, Long ruleId) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        tenantSecurityService.requireBusinessInventoryWriteAccess(vehicle.getBusiness().getId());

        PricingRule rule = pricingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing rule not found: " + ruleId));
        if (rule.getVehicle() == null || !rule.getVehicle().getId().equals(vehicle.getId())) {
            throw new ResourceNotFoundException("Pricing rule does not belong to vehicle: " + vehiclePublicId);
        }

        rule.setActive(false);
        pricingRuleRepository.save(rule);
        auditLogService.record(
                tenantSecurityService.getCurrentUserIdOrNull(),
                "DEACTIVATE_PRICING_RULE",
                "PricingRule",
                String.valueOf(ruleId),
                Map.of("active", true),
                Map.of("active", false, "vehiclePublicId", vehicle.getPublicId())
        );
    }

    @Transactional(readOnly = true)
    public String getPlatformCurrency() {
        return systemSettingRepository.findBySettingKey(SETTING_PLATFORM_CURRENCY)
                .map(SystemSetting::getSettingValue)
                .map(v -> v.trim().toUpperCase(Locale.ROOT))
                .filter(v -> v.length() == 3)
                .orElse("INR");
    }

    @Transactional(readOnly = true)
    public BigDecimal getPlatformGstPercentage() {
        return systemSettingRepository.findBySettingKey(SETTING_PLATFORM_GST)
                .map(SystemSetting::getSettingValue)
                .map(v -> {
                    try {
                        return new BigDecimal(v.trim());
                    } catch (NumberFormatException ex) {
                        return new BigDecimal("5.00");
                    }
                })
                .orElse(new BigDecimal("5.00"));
    }

    public static VehiclePricingPlanDto toDto(VehiclePricingPlan p, BigDecimal gstPercentage) {
        return new VehiclePricingPlanDto(
                p.getPublicId(),
                p.getCurrency(),
                p.getHourlyRate(),
                p.getDailyRate(),
                p.getWeeklyRate(),
                p.getSecurityDeposit(),
                p.getMinRentalHours(),
                p.getMaxRentalDays(),
                p.getIncludedKmPerDay(),
                p.getExtraKmCharge(),
                p.getExtraHourCharge(),
                p.getCleaningFee(),
                p.getDeliveryFee(),
                p.getAdditionalChargesDescription(),
                p.getLateReturnFeePerHour(),
                p.getLateReturnFlatPenalty(),
                p.getLateReturnGraceMinutes(),
                p.getValidFrom(),
                p.getValidTo(),
                p.isActive(),
                gstPercentage
        );
    }

    public static PricingRuleDto toRuleDto(PricingRule rule) {
        return new PricingRuleDto(
                rule.getId(),
                rule.getRuleName(),
                rule.getRuleType(),
                rule.getMinDays(),
                rule.getAdjustmentType(),
                rule.getAdjustmentValue(),
                rule.isActive()
        );
    }

    private static boolean isEffectiveAt(VehiclePricingPlanDto plan, Instant now) {
        return (plan.validFrom() == null || !plan.validFrom().isAfter(now))
                && (plan.validTo() == null || plan.validTo().isAfter(now));
    }

    private VehiclePricingPlanDto synthesizeFallbackPlan(Vehicle v, String currency, BigDecimal gstPercentage) {
        return new VehiclePricingPlanDto(
                null,
                currency,
                null,
                v.getDailyRate(),
                null,
                v.getSecurityDeposit(),
                6,
                30,
                null,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                null,
                30,
                null,
                null,
                true,
                gstPercentage
        );
    }

    private Vehicle requireNonDeletedVehicle(String vehiclePublicId) {
        return vehicleRepository.findByPublicIdAndDeletedAtIsNull(vehiclePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehiclePublicId));
    }

    private static String normalizeBlank(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}

