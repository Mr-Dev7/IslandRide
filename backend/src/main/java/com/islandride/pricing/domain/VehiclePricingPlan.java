package com.islandride.pricing.domain;

import com.islandride.core.domain.AuditableEntity;
import com.islandride.inventory.domain.Vehicle;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicle_pricing_configs")
public class VehiclePricingPlan extends AuditableEntity {

    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    private String publicId = "prc_" + UUID.randomUUID().toString().replace("-", "");

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "daily_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "weekly_rate", precision = 10, scale = 2)
    private BigDecimal weeklyRate;

    @Column(name = "security_deposit", nullable = false, precision = 10, scale = 2)
    private BigDecimal securityDeposit = BigDecimal.ZERO;

    @Column(name = "min_rental_hours", nullable = false)
    private int minRentalHours = 6;

    @Column(name = "max_rental_days", nullable = false)
    private int maxRentalDays = 30;

    @Column(name = "included_km_per_day")
    private Integer includedKmPerDay;

    @Column(name = "extra_km_charge", precision = 10, scale = 2)
    private BigDecimal extraKmCharge;

    @Column(name = "extra_hour_charge", precision = 10, scale = 2)
    private BigDecimal extraHourCharge;

    @Column(name = "cleaning_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal cleaningFee = BigDecimal.ZERO;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "additional_charges_description", length = 500)
    private String additionalChargesDescription;

    @Column(name = "late_return_fee_per_hour", precision = 10, scale = 2)
    private BigDecimal lateReturnFeePerHour;

    @Column(name = "late_return_flat_penalty", precision = 10, scale = 2)
    private BigDecimal lateReturnFlatPenalty;

    @Column(name = "late_return_grace_minutes", nullable = false)
    private int lateReturnGraceMinutes = 30;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(BigDecimal hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public BigDecimal getDailyRate() {
        return dailyRate;
    }

    public void setDailyRate(BigDecimal dailyRate) {
        this.dailyRate = dailyRate;
    }

    public BigDecimal getWeeklyRate() {
        return weeklyRate;
    }

    public void setWeeklyRate(BigDecimal weeklyRate) {
        this.weeklyRate = weeklyRate;
    }

    public BigDecimal getSecurityDeposit() {
        return securityDeposit;
    }

    public void setSecurityDeposit(BigDecimal securityDeposit) {
        this.securityDeposit = securityDeposit;
    }

    public int getMinRentalHours() {
        return minRentalHours;
    }

    public void setMinRentalHours(int minRentalHours) {
        this.minRentalHours = minRentalHours;
    }

    public int getMaxRentalDays() {
        return maxRentalDays;
    }

    public void setMaxRentalDays(int maxRentalDays) {
        this.maxRentalDays = maxRentalDays;
    }

    public Integer getIncludedKmPerDay() {
        return includedKmPerDay;
    }

    public void setIncludedKmPerDay(Integer includedKmPerDay) {
        this.includedKmPerDay = includedKmPerDay;
    }

    public BigDecimal getExtraKmCharge() {
        return extraKmCharge;
    }

    public void setExtraKmCharge(BigDecimal extraKmCharge) {
        this.extraKmCharge = extraKmCharge;
    }

    public BigDecimal getExtraHourCharge() {
        return extraHourCharge;
    }

    public void setExtraHourCharge(BigDecimal extraHourCharge) {
        this.extraHourCharge = extraHourCharge;
    }

    public BigDecimal getCleaningFee() {
        return cleaningFee;
    }

    public void setCleaningFee(BigDecimal cleaningFee) {
        this.cleaningFee = cleaningFee;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public void setDeliveryFee(BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    public String getAdditionalChargesDescription() {
        return additionalChargesDescription;
    }

    public void setAdditionalChargesDescription(String additionalChargesDescription) {
        this.additionalChargesDescription = additionalChargesDescription;
    }

    public BigDecimal getLateReturnFeePerHour() {
        return lateReturnFeePerHour;
    }

    public void setLateReturnFeePerHour(BigDecimal lateReturnFeePerHour) {
        this.lateReturnFeePerHour = lateReturnFeePerHour;
    }

    public BigDecimal getLateReturnFlatPenalty() {
        return lateReturnFlatPenalty;
    }

    public void setLateReturnFlatPenalty(BigDecimal lateReturnFlatPenalty) {
        this.lateReturnFlatPenalty = lateReturnFlatPenalty;
    }

    public int getLateReturnGraceMinutes() {
        return lateReturnGraceMinutes;
    }

    public void setLateReturnGraceMinutes(int lateReturnGraceMinutes) {
        this.lateReturnGraceMinutes = lateReturnGraceMinutes;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public void setValidTo(Instant validTo) {
        this.validTo = validTo;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

