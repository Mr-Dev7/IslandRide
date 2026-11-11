package com.islandride.pricing.service;

import com.islandride.administration.domain.SystemSetting;
import com.islandride.administration.repository.SystemSettingRepository;
import com.islandride.booking.domain.GatewayFeeTreatment;
import com.islandride.booking.domain.SecurityDepositCollectionMethod;
import com.islandride.pricing.domain.PartnerPolicy;
import com.islandride.pricing.repository.PartnerPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;

@Service
public class BookingPolicyService {

    public static final String KEY_COMMISSION_PERCENT = "platform.commission.percentage";
    public static final String KEY_HOLD_DURATION_SECONDS = "inventory.hold.duration_seconds";
    public static final String KEY_APPROVAL_GT_24H_HOURS = "booking.approval.window.gt_24h_hours";
    public static final String KEY_APPROVAL_6_TO_24H_HOURS = "booking.approval.window.6_to_24h_hours";
    public static final String KEY_APPROVAL_LT_6H_MINUTES = "booking.approval.window.lt_6h_minutes";
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

    private final PartnerPolicyRepository partnerPolicyRepository;
    private final SystemSettingRepository systemSettingRepository;

    public BookingPolicyService(PartnerPolicyRepository partnerPolicyRepository,
                                SystemSettingRepository systemSettingRepository) {
        this.partnerPolicyRepository = partnerPolicyRepository;
        this.systemSettingRepository = systemSettingRepository;
    }

    @Transactional(readOnly = true)
    public EffectiveBookingPolicy resolvePolicy(Long businessId) {
        PartnerPolicy partnerPolicy = businessId != null
                ? partnerPolicyRepository.findByBusinessId(businessId).orElse(null)
                : null;

        String currency = getStringSetting(VehiclePricingService.SETTING_PLATFORM_CURRENCY, "INR").toUpperCase(Locale.ROOT);
        BigDecimal gstPercentage = getDecimalSetting(VehiclePricingService.SETTING_PLATFORM_GST, new BigDecimal("5.00"));
        BigDecimal commissionPercentage = getDecimalSetting(KEY_COMMISSION_PERCENT, new BigDecimal("4.00"));

        int minRentalHours = partnerPolicy != null ? partnerPolicy.getMinRentalHours() : 6;
        int maxRentalDays = partnerPolicy != null ? partnerPolicy.getMaxRentalDays() : 30;
        int turnaroundBufferMinutes = partnerPolicy != null ? partnerPolicy.getTurnaroundBufferMinutes() : 60;
        BigDecimal advancePercentage = partnerPolicy != null
                ? partnerPolicy.getAdvancePaymentPercentage()
                : new BigDecimal("25.00");
        int fullCutoffHours = partnerPolicy != null
                ? partnerPolicy.getFullPaymentCutoffHours()
                : getIntSetting(KEY_FULL_CUTOFF_HOURS, 12);
        int cancellationDeadlineHours = partnerPolicy != null ? partnerPolicy.getCancellationDeadlineHours() : 24;
        boolean freeCancellationAllowed = partnerPolicy == null || partnerPolicy.isFreeCancellationAllowed();
        BigDecimal cancellationFeePct = partnerPolicy != null
                ? partnerPolicy.getCancellationFeePercentage()
                : new BigDecimal("10.00");
        int inspectionWindowHours = partnerPolicy != null ? partnerPolicy.getDepositInspectionWindowHours() : 24;
        SecurityDepositCollectionMethod depositMethod = partnerPolicy != null
                ? partnerPolicy.getSecurityDepositCollectionMethod()
                : getEnumSetting(KEY_DEPOSIT_COLLECTION_DEFAULT, SecurityDepositCollectionMethod.class, SecurityDepositCollectionMethod.AT_PICKUP);

        int noShowGraceMinutes = partnerPolicy != null
                ? partnerPolicy.getNoShowGraceMinutes()
                : getIntSetting(KEY_NO_SHOW_GRACE_MINUTES, 120);
        BigDecimal noShowPenaltyPct = partnerPolicy != null
                ? partnerPolicy.getNoShowPenaltyPercentage()
                : getDecimalSetting(KEY_NO_SHOW_PENALTY_PERCENT, new BigDecimal("100.00"));
        GatewayFeeTreatment gatewayFeeTreatment = partnerPolicy != null
                ? partnerPolicy.getGatewayFeeTreatment()
                : getEnumSetting(KEY_GATEWAY_FEE_DEFAULT, GatewayFeeTreatment.class, GatewayFeeTreatment.PLATFORM_ABSORBED);
        boolean rescheduleAllowed = partnerPolicy == null || partnerPolicy.isReschedulingAllowedForDisruption();

        int minLeadTimeMinutes = getIntSetting(KEY_MIN_LEAD_TIME_MINUTES, 30);
        int holdDurationSeconds = getIntSetting(KEY_HOLD_DURATION_SECONDS, 600);
        int gt24hHours = getIntSetting(KEY_APPROVAL_GT_24H_HOURS, 6);
        int sixTo24hHours = getIntSetting(KEY_APPROVAL_6_TO_24H_HOURS, 2);
        int lt6hMinutes = getIntSetting(KEY_APPROVAL_LT_6H_MINUTES, 45);

        int payWindowHours = getIntSetting(KEY_APPROVED_PAY_WINDOW_HOURS, 2);
        int payUrgentMinutes = getIntSetting(KEY_APPROVED_PAY_URGENT_MINUTES, 30);
        int payUrgentThresholdHours = getIntSetting(KEY_APPROVED_PAY_URGENT_THRESHOLD_HOURS, 6);

        ZoneId zoneId = ZoneId.of(getStringSetting(KEY_OP_HOURS_TIMEZONE, "Asia/Kolkata"));
        LocalTime defaultOpen = LocalTime.parse(getStringSetting(KEY_OP_HOURS_DEFAULT_OPEN, "07:00"));
        LocalTime defaultClose = LocalTime.parse(getStringSetting(KEY_OP_HOURS_DEFAULT_CLOSE, "21:00"));

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

    private String getStringSetting(String key, String fallback) {
        return systemSettingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .filter(val -> !val.isEmpty())
                .orElse(fallback);
    }

    private int getIntSetting(String key, int fallback) {
        return systemSettingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .map(Integer::parseInt)
                .orElse(fallback);
    }

    private BigDecimal getDecimalSetting(String key, BigDecimal fallback) {
        return systemSettingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .map(BigDecimal::new)
                .orElse(fallback);
    }

    private <E extends Enum<E>> E getEnumSetting(String key, Class<E> enumClass, E fallback) {
        return systemSettingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .map(val -> val.toUpperCase(Locale.ROOT))
                .map(val -> Enum.valueOf(enumClass, val))
                .orElse(fallback);
    }
}

