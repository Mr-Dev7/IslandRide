package com.islandride.pricing.service;

import com.islandride.administration.domain.SystemSetting;
import com.islandride.administration.repository.SystemSettingRepository;
import com.islandride.booking.domain.GatewayFeeTreatment;
import com.islandride.booking.domain.SecurityDepositCollectionMethod;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.pricing.domain.PartnerPolicy;
import com.islandride.pricing.repository.PartnerPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Service
public class BookingPolicyService {

    // V8 seeded keys
    public static final String KEY_HOLD_DURATION_SECONDS = "hold.duration.seconds";
    public static final String KEY_TURNAROUND_BUFFER_MINUTES = "turnaround.buffer.minutes.default";
    public static final String KEY_ADVANCE_PAYMENT_PERCENT = "advance.payment.percentage.default";
    public static final String KEY_COMMISSION_PERCENT = "platform.commission.rate.default";
    public static final String KEY_APPROVAL_GT_24H_HOURS = "approval.deadline.hours.long";
    public static final String KEY_APPROVAL_6_TO_24H_HOURS = "approval.deadline.hours.medium";
    public static final String KEY_APPROVAL_LT_6H_MINUTES = "approval.deadline.minutes.urgent";

    // V11 seeded keys
    public static final String KEY_FULL_CUTOFF_HOURS = "booking.full_payment_cutoff_hours.default";
    public static final String KEY_APPROVED_PAY_WINDOW_HOURS = "booking.approved.payment_window_hours";
    public static final String KEY_APPROVED_PAY_URGENT_MINUTES = "booking.approved.payment_window_urgent_minutes";
    public static final String KEY_APPROVED_PAY_URGENT_THRESHOLD_HOURS = "booking.approved.payment_window_urgent_threshold_hours";
    public static final String KEY_NO_SHOW_GRACE_MINUTES = "booking.no_show.grace_minutes.default";
    public static final String KEY_NO_SHOW_PENALTY_PERCENT = "booking.no_show.penalty_percentage.default";
    public static final String KEY_DEPOSIT_COLLECTION_DEFAULT = "booking.deposit.collection_method.default";
    public static final String KEY_GATEWAY_FEE_DEFAULT = "booking.gateway_fee.treatment.default";
    public static final String KEY_MIN_LEAD_TIME_MINUTES = "booking.lead_time.min_minutes";
    public static final String KEY_OP_HOURS_TIMEZONE = "booking.operating_hours.timezone";
    public static final String KEY_OP_HOURS_DEFAULT_OPEN = "booking.operating_hours.default_open";
    public static final String KEY_OP_HOURS_DEFAULT_CLOSE = "booking.operating_hours.default_close";

    // V12 seeded keys
    public static final String KEY_MIN_RENTAL_HOURS = "booking.min_rental_hours.default";
    public static final String KEY_MAX_RENTAL_DAYS = "booking.max_rental_days.default";
    public static final String KEY_CANCELLATION_DEADLINE_HOURS = "booking.cancellation.deadline_hours.default";
    public static final String KEY_FREE_CANCELLATION_ALLOWED = "booking.cancellation.free_allowed.default";
    public static final String KEY_CANCELLATION_FEE_PERCENT = "booking.cancellation.fee_percentage.default";
    public static final String KEY_DEPOSIT_INSPECTION_WINDOW_HOURS = "booking.deposit.inspection_window_hours.default";
    public static final String KEY_DISRUPTION_RESCHEDULING_ALLOWED = "booking.disruption.rescheduling_allowed.default";

    private final PartnerPolicyRepository partnerPolicyRepository;
    private final SystemSettingRepository systemSettingRepository;

    public BookingPolicyService(PartnerPolicyRepository partnerPolicyRepository,
                                SystemSettingRepository systemSettingRepository) {
        this.partnerPolicyRepository = partnerPolicyRepository;
        this.systemSettingRepository = systemSettingRepository;
    }

    @Transactional(readOnly = true)
    public EffectiveBookingPolicy resolvePolicy(Long businessId) {
        return resolvePolicy(businessId, null);
    }

    @Transactional(readOnly = true)
    public EffectiveBookingPolicy resolvePolicy(Vehicle vehicle) {
        if (vehicle == null) {
            throw new IllegalArgumentException("Vehicle cannot be null when resolving vehicle booking policy.");
        }
        Long businessId = vehicle.getBusiness() != null ? vehicle.getBusiness().getId() : null;
        return resolvePolicy(businessId, vehicle.getTurnaroundBufferMinutes());
    }

    @Transactional(readOnly = true)
    public EffectiveBookingPolicy resolvePolicy(Long businessId, Integer vehicleTurnaroundBufferOverride) {
        PartnerPolicy partnerPolicy = businessId != null
                ? partnerPolicyRepository.findByBusinessId(businessId).orElse(null)
                : null;

        String currency = requireStringSetting(VehiclePricingService.SETTING_PLATFORM_CURRENCY).toUpperCase(Locale.ROOT);
        BigDecimal gstPercentage = requireDecimalSetting(VehiclePricingService.SETTING_PLATFORM_GST);
        BigDecimal commissionPercentage = requireDecimalSetting(KEY_COMMISSION_PERCENT);

        int minRentalHours = (partnerPolicy != null && partnerPolicy.getMinRentalHours() != null)
                ? partnerPolicy.getMinRentalHours()
                : requireIntSetting(KEY_MIN_RENTAL_HOURS);
        int maxRentalDays = (partnerPolicy != null && partnerPolicy.getMaxRentalDays() != null)
                ? partnerPolicy.getMaxRentalDays()
                : requireIntSetting(KEY_MAX_RENTAL_DAYS);
        int turnaroundBufferMinutes = resolveTurnaroundBuffer(
                vehicleTurnaroundBufferOverride,
                partnerPolicy != null ? partnerPolicy.getTurnaroundBufferMinutes() : null
        );
        BigDecimal advancePercentage = (partnerPolicy != null && partnerPolicy.getAdvancePaymentPercentage() != null)
                ? partnerPolicy.getAdvancePaymentPercentage()
                : requireDecimalSetting(KEY_ADVANCE_PAYMENT_PERCENT);
        int fullCutoffHours = (partnerPolicy != null && partnerPolicy.getFullPaymentCutoffHours() != null)
                ? partnerPolicy.getFullPaymentCutoffHours()
                : requireIntSetting(KEY_FULL_CUTOFF_HOURS);
        int cancellationDeadlineHours = (partnerPolicy != null && partnerPolicy.getCancellationDeadlineHours() != null)
                ? partnerPolicy.getCancellationDeadlineHours()
                : requireIntSetting(KEY_CANCELLATION_DEADLINE_HOURS);
        boolean freeCancellationAllowed = (partnerPolicy != null && partnerPolicy.isFreeCancellationAllowed() != null)
                ? partnerPolicy.isFreeCancellationAllowed()
                : requireBooleanSetting(KEY_FREE_CANCELLATION_ALLOWED);
        BigDecimal cancellationFeePct = (partnerPolicy != null && partnerPolicy.getCancellationFeePercentage() != null)
                ? partnerPolicy.getCancellationFeePercentage()
                : requireDecimalSetting(KEY_CANCELLATION_FEE_PERCENT);
        int inspectionWindowHours = (partnerPolicy != null && partnerPolicy.getDepositInspectionWindowHours() != null)
                ? partnerPolicy.getDepositInspectionWindowHours()
                : requireIntSetting(KEY_DEPOSIT_INSPECTION_WINDOW_HOURS);
        SecurityDepositCollectionMethod depositMethod = (partnerPolicy != null && partnerPolicy.getSecurityDepositCollectionMethod() != null)
                ? partnerPolicy.getSecurityDepositCollectionMethod()
                : requireEnumSetting(KEY_DEPOSIT_COLLECTION_DEFAULT, SecurityDepositCollectionMethod.class);

        int noShowGraceMinutes = (partnerPolicy != null && partnerPolicy.getNoShowGraceMinutes() != null)
                ? partnerPolicy.getNoShowGraceMinutes()
                : requireIntSetting(KEY_NO_SHOW_GRACE_MINUTES);
        BigDecimal noShowPenaltyPct = (partnerPolicy != null && partnerPolicy.getNoShowPenaltyPercentage() != null)
                ? partnerPolicy.getNoShowPenaltyPercentage()
                : requireDecimalSetting(KEY_NO_SHOW_PENALTY_PERCENT);
        GatewayFeeTreatment gatewayFeeTreatment = (partnerPolicy != null && partnerPolicy.getGatewayFeeTreatment() != null)
                ? partnerPolicy.getGatewayFeeTreatment()
                : requireEnumSetting(KEY_GATEWAY_FEE_DEFAULT, GatewayFeeTreatment.class);
        boolean rescheduleAllowed = (partnerPolicy != null && partnerPolicy.isReschedulingAllowedForDisruption() != null)
                ? partnerPolicy.isReschedulingAllowedForDisruption()
                : requireBooleanSetting(KEY_DISRUPTION_RESCHEDULING_ALLOWED);

        int minLeadTimeMinutes = requireIntSetting(KEY_MIN_LEAD_TIME_MINUTES);
        int holdDurationSeconds = requireIntSetting(KEY_HOLD_DURATION_SECONDS);
        int gt24hHours = requireIntSetting(KEY_APPROVAL_GT_24H_HOURS);
        int sixTo24hHours = requireIntSetting(KEY_APPROVAL_6_TO_24H_HOURS);
        int lt6hMinutes = requireIntSetting(KEY_APPROVAL_LT_6H_MINUTES);

        int payWindowHours = requireIntSetting(KEY_APPROVED_PAY_WINDOW_HOURS);
        int payUrgentMinutes = requireIntSetting(KEY_APPROVED_PAY_URGENT_MINUTES);
        int payUrgentThresholdHours = requireIntSetting(KEY_APPROVED_PAY_URGENT_THRESHOLD_HOURS);

        ZoneId zoneId = ZoneId.of(requireStringSetting(KEY_OP_HOURS_TIMEZONE));
        LocalTime defaultOpen = parseLocalTime(KEY_OP_HOURS_DEFAULT_OPEN, requireStringSetting(KEY_OP_HOURS_DEFAULT_OPEN));
        LocalTime defaultClose = parseLocalTime(KEY_OP_HOURS_DEFAULT_CLOSE, requireStringSetting(KEY_OP_HOURS_DEFAULT_CLOSE));

        return new EffectiveBookingPolicy(
                currency,
                gstPercentage,
                commissionPercentage,
                minRentalHours,
                maxRentalDays,
                turnaroundBufferMinutes,
                advancePercentage,
                fullCutoffHours,
                cancellationDeadlineHours,
                freeCancellationAllowed,
                cancellationFeePct,
                inspectionWindowHours,
                depositMethod,
                noShowGraceMinutes,
                noShowPenaltyPct,
                gatewayFeeTreatment,
                rescheduleAllowed,
                minLeadTimeMinutes,
                holdDurationSeconds,
                gt24hHours,
                sixTo24hHours,
                lt6hMinutes,
                payWindowHours,
                payUrgentMinutes,
                payUrgentThresholdHours,
                zoneId,
                defaultOpen,
                defaultClose
        );
    }

    @Transactional(readOnly = true)
    public int resolveTurnaroundBufferMinutes(Vehicle vehicle) {
        if (vehicle == null) {
            return resolveDefaultTurnaroundBufferMinutes();
        }
        if (vehicle.getTurnaroundBufferMinutes() != null) {
            return vehicle.getTurnaroundBufferMinutes();
        }
        Long businessId = vehicle.getBusiness() != null ? vehicle.getBusiness().getId() : null;
        Integer partnerOverride = businessId != null
                ? partnerPolicyRepository.findByBusinessId(businessId)
                        .map(PartnerPolicy::getTurnaroundBufferMinutes)
                        .orElse(null)
                : null;
        return partnerOverride != null ? partnerOverride : resolveDefaultTurnaroundBufferMinutes();
    }

    @Transactional(readOnly = true)
    public int resolveDefaultTurnaroundBufferMinutes() {
        return requireIntSetting(KEY_TURNAROUND_BUFFER_MINUTES);
    }

    private int resolveTurnaroundBuffer(Integer vehicleOverride, Integer partnerOverride) {
        if (vehicleOverride != null) {
            return vehicleOverride;
        }
        if (partnerOverride != null) {
            return partnerOverride;
        }
        return resolveDefaultTurnaroundBufferMinutes();
    }

    private String requireStringSetting(String key) {
        return systemSettingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .filter(val -> !val.isEmpty())
                .orElseThrow(() -> new IllegalStateException("Missing or blank required system setting: " + key));
    }

    private int requireIntSetting(String key) {
        String raw = requireStringSetting(key);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid integer value for system setting '" + key + "': " + raw, ex);
        }
    }

    private BigDecimal requireDecimalSetting(String key) {
        String raw = requireStringSetting(key);
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid decimal value for system setting '" + key + "': " + raw, ex);
        }
    }

    private boolean requireBooleanSetting(String key) {
        String raw = requireStringSetting(key);
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        throw new IllegalStateException("Invalid boolean value for system setting '" + key + "': " + raw);
    }

    private <E extends Enum<E>> E requireEnumSetting(String key, Class<E> enumClass) {
        String raw = requireStringSetting(key).toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(enumClass, raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid enum value for system setting '" + key + "': " + raw, ex);
        }
    }

    private static LocalTime parseLocalTime(String key, String raw) {
        try {
            return LocalTime.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new IllegalStateException("Invalid HH:mm time value for system setting '" + key + "': " + raw, ex);
        }
    }
}

