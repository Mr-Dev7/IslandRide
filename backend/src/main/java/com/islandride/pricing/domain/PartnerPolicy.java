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

    @Column(name = "min_rental_hours")
    private Integer minRentalHours;

    @Column(name = "max_rental_days")
    private Integer maxRentalDays;

    @Column(name = "turnaround_buffer_minutes")
    private Integer turnaroundBufferMinutes;

    @Column(name = "advance_payment_percentage", precision = 5, scale = 2)
    private BigDecimal advancePaymentPercentage;

    @Column(name = "full_payment_cutoff_hours")
    private Integer fullPaymentCutoffHours;

    @Column(name = "cancellation_deadline_hours")
    private Integer cancellationDeadlineHours;

    @Column(name = "free_cancellation_allowed")
    private Boolean freeCancellationAllowed;

    @Column(name = "cancellation_fee_percentage", precision = 5, scale = 2)
    private BigDecimal cancellationFeePercentage;

    @Column(name = "no_show_grace_minutes")
    private Integer noShowGraceMinutes;

    @Column(name = "no_show_penalty_percentage", precision = 5, scale = 2)
    private BigDecimal noShowPenaltyPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_fee_treatment", length = 40)
    private GatewayFeeTreatment gatewayFeeTreatment;

    @Column(name = "rescheduling_allowed_for_disruption")
    private Boolean reschedulingAllowedForDisruption;

    @Column(name = "deposit_inspection_window_hours")
    private Integer depositInspectionWindowHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "security_deposit_collection_method", length = 30)
    private SecurityDepositCollectionMethod securityDepositCollectionMethod;

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

    public Integer getMinRentalHours() {
        return minRentalHours;
    }

    public void setMinRentalHours(Integer minRentalHours) {
        this.minRentalHours = minRentalHours;
    }

    public Integer getMaxRentalDays() {
        return maxRentalDays;
    }

    public void setMaxRentalDays(Integer maxRentalDays) {
        this.maxRentalDays = maxRentalDays;
    }

    public Integer getTurnaroundBufferMinutes() {
        return turnaroundBufferMinutes;
    }

    public void setTurnaroundBufferMinutes(Integer turnaroundBufferMinutes) {
        this.turnaroundBufferMinutes = turnaroundBufferMinutes;
    }

    public BigDecimal getAdvancePaymentPercentage() {
        return advancePaymentPercentage;
    }

    public void setAdvancePaymentPercentage(BigDecimal advancePaymentPercentage) {
        this.advancePaymentPercentage = advancePaymentPercentage;
    }

    public Integer getCancellationDeadlineHours() {
        return cancellationDeadlineHours;
    }

    public void setCancellationDeadlineHours(Integer cancellationDeadlineHours) {
        this.cancellationDeadlineHours = cancellationDeadlineHours;
    }

    public Boolean isFreeCancellationAllowed() {
        return freeCancellationAllowed;
    }

    public void setFreeCancellationAllowed(Boolean freeCancellationAllowed) {
        this.freeCancellationAllowed = freeCancellationAllowed;
    }

    public BigDecimal getCancellationFeePercentage() {
        return cancellationFeePercentage;
    }

    public void setCancellationFeePercentage(BigDecimal cancellationFeePercentage) {
        this.cancellationFeePercentage = cancellationFeePercentage;
    }

    public Integer getDepositInspectionWindowHours() {
        return depositInspectionWindowHours;
    }

    public void setDepositInspectionWindowHours(Integer depositInspectionWindowHours) {
        this.depositInspectionWindowHours = depositInspectionWindowHours;
    }

    public Integer getFullPaymentCutoffHours() {
        return fullPaymentCutoffHours;
    }

    public void setFullPaymentCutoffHours(Integer fullPaymentCutoffHours) {
        this.fullPaymentCutoffHours = fullPaymentCutoffHours;
    }

    public Integer getNoShowGraceMinutes() {
        return noShowGraceMinutes;
    }

    public void setNoShowGraceMinutes(Integer noShowGraceMinutes) {
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

    public Boolean isReschedulingAllowedForDisruption() {
        return reschedulingAllowedForDisruption;
    }

    public void setReschedulingAllowedForDisruption(Boolean reschedulingAllowedForDisruption) {
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

