package com.islandride.pricing.domain;

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

    @Column(name = "cancellation_deadline_hours", nullable = false)
    private int cancellationDeadlineHours = 24;

    @Column(name = "free_cancellation_allowed", nullable = false)
    private boolean freeCancellationAllowed = true;

    @Column(name = "cancellation_fee_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal cancellationFeePercentage = new BigDecimal("10.00");

    @Column(name = "deposit_inspection_window_hours", nullable = false)
    private int depositInspectionWindowHours = 24;

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

