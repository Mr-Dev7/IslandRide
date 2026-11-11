package com.islandride.pricing.domain;

import com.islandride.booking.domain.GatewayFeeTreatment;
import com.islandride.booking.domain.SecurityDepositCollectionMethod;
import com.islandride.core.domain.BaseEntity;
import com.islandride.partner.domain.Business;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "partner_policies")
public class PartnerPolicy extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false, unique = true)
    private Business business;

    @Column(name = "min_rental_hours", nullable = false)
    private int minRentalHours = 6;

    @Column(name = "max_rental_days", nullable = false)
    private int maxRentalDays = 30;

    @Column(name = "turnaround_buffer_minutes", nullable = false)
    private int turnaroundBufferMinutes = 60;

    @Column(name = "advance_payment_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal advancePaymentPercentage = new BigDecimal("25.00");

    @Column(name = "full_payment_cutoff_hours", nullable = false)
    private int fullPaymentCutoffHours = 12;

    @Column(name = "cancellation_deadline_hours", nullable = false)
    private int cancellationDeadlineHours = 24;

    @Column(name = "free_cancellation_allowed", nullable = false)
    private boolean freeCancellationAllowed = true;

    @Column(name = "cancellation_fee_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal cancellationFeePercentage = new BigDecimal("10.00");

    @Column(name = "no_show_grace_minutes", nullable = false)
    private int noShowGraceMinutes = 120;

    @Column(name = "no_show_penalty_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal noShowPenaltyPercentage = new BigDecimal("100.00");

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_fee_treatment", nullable = false, length = 40)
    private GatewayFeeTreatment gatewayFeeTreatment = GatewayFeeTreatment.PLATFORM_ABSORBED;

    @Column(name = "rescheduling_allowed_for_disruption", nullable = false)
    private boolean reschedulingAllowedForDisruption = true;

    @Column(name = "deposit_inspection_window_hours", nullable = false)
    private int depositInspectionWindowHours = 24;

    @Enumerated(EnumType.STRING)
    @Column(name = "security_deposit_collection_method", nullable = false, length = 30)
    private SecurityDepositCollectionMethod securityDepositCollectionMethod = SecurityDepositCollectionMethod.AT_PICKUP;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Business getBusiness() {
        return business;
    }

    public void setBusiness(Business business) {
        this.business = business;
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

    public int getTurnaroundBufferMinutes() {
        return turnaroundBufferMinutes;
    }

    public void setTurnaroundBufferMinutes(int turnaroundBufferMinutes) {
        this.turnaroundBufferMinutes = turnaroundBufferMinutes;
    }

    public BigDecimal getAdvancePaymentPercentage() {
        return advancePaymentPercentage;
    }

    public void setAdvancePaymentPercentage(BigDecimal advancePaymentPercentage) {
        this.advancePaymentPercentage = advancePaymentPercentage;
    }

    public int getCancellationDeadlineHours() {
        return cancellationDeadlineHours;
    }

    public void setCancellationDeadlineHours(int cancellationDeadlineHours) {
        this.cancellationDeadlineHours = cancellationDeadlineHours;
    }

    public boolean isFreeCancellationAllowed() {
        return freeCancellationAllowed;
    }

    public void setFreeCancellationAllowed(boolean freeCancellationAllowed) {
        this.freeCancellationAllowed = freeCancellationAllowed;
    }

    public BigDecimal getCancellationFeePercentage() {
        return cancellationFeePercentage;
    }

    public void setCancellationFeePercentage(BigDecimal cancellationFeePercentage) {
        this.cancellationFeePercentage = cancellationFeePercentage;
    }

    public int getDepositInspectionWindowHours() {
        return depositInspectionWindowHours;
    }

    public void setDepositInspectionWindowHours(int depositInspectionWindowHours) {
        this.depositInspectionWindowHours = depositInspectionWindowHours;
    }

    public int getFullPaymentCutoffHours() {
        return fullPaymentCutoffHours;
    }

    public void setFullPaymentCutoffHours(int fullPaymentCutoffHours) {
        this.fullPaymentCutoffHours = fullPaymentCutoffHours;
    }

    public int getNoShowGraceMinutes() {
        return noShowGraceMinutes;
    }

    public void setNoShowGraceMinutes(int noShowGraceMinutes) {
        this.noShowGraceMinutes = noShowGraceMinutes;
    }

    public BigDecimal getNoShowPenaltyPercentage() {
        return noShowPenaltyPercentage;
    }

    public void setNoShowPenaltyPercentage(BigDecimal noShowPenaltyPercentage) {
        this.noShowPenaltyPercentage = noShowPenaltyPercentage;
    }

    public GatewayFeeTreatment getGatewayFeeTreatment() {
        return gatewayFeeTreatment;
    }

    public void setGatewayFeeTreatment(GatewayFeeTreatment gatewayFeeTreatment) {
        this.gatewayFeeTreatment = gatewayFeeTreatment;
    }

    public boolean isReschedulingAllowedForDisruption() {
        return reschedulingAllowedForDisruption;
    }

    public void setReschedulingAllowedForDisruption(boolean reschedulingAllowedForDisruption) {
        this.reschedulingAllowedForDisruption = reschedulingAllowedForDisruption;
    }

    public SecurityDepositCollectionMethod getSecurityDepositCollectionMethod() {
        return securityDepositCollectionMethod;
    }

    public void setSecurityDepositCollectionMethod(SecurityDepositCollectionMethod securityDepositCollectionMethod) {
        this.securityDepositCollectionMethod = securityDepositCollectionMethod;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

