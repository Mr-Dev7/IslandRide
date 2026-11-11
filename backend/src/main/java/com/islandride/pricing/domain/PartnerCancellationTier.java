package com.islandride.pricing.domain;

import com.islandride.core.domain.BaseEntity;
import com.islandride.partner.domain.Business;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "partner_cancellation_tiers")
public class PartnerCancellationTier extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(name = "min_hours_before_pickup", nullable = false)
    private int minHoursBeforePickup;

    @Column(name = "max_hours_before_pickup")
    private Integer maxHoursBeforePickup;

    @Column(name = "fee_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal feePercentage = BigDecimal.ZERO;

    @Column(name = "flat_fee_amount", precision = 10, scale = 2)
    private BigDecimal flatFeeAmount;

    @Column(name = "applies_to_deposit", nullable = false)
    private boolean appliesToDeposit = false;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Business getBusiness() {
        return business;
    }

    public void setBusiness(Business business) {
        this.business = business;
    }

    public int getMinHoursBeforePickup() {
        return minHoursBeforePickup;
    }

    public void setMinHoursBeforePickup(int minHoursBeforePickup) {
        this.minHoursBeforePickup = minHoursBeforePickup;
    }

    public Integer getMaxHoursBeforePickup() {
        return maxHoursBeforePickup;
    }

    public void setMaxHoursBeforePickup(Integer maxHoursBeforePickup) {
        this.maxHoursBeforePickup = maxHoursBeforePickup;
    }

    public BigDecimal getFeePercentage() {
        return feePercentage;
    }

    public void setFeePercentage(BigDecimal feePercentage) {
        this.feePercentage = feePercentage;
    }

    public BigDecimal getFlatFeeAmount() {
        return flatFeeAmount;
    }

    public void setFlatFeeAmount(BigDecimal flatFeeAmount) {
        this.flatFeeAmount = flatFeeAmount;
    }

    public boolean isAppliesToDeposit() {
        return appliesToDeposit;
    }

    public void setAppliesToDeposit(boolean appliesToDeposit) {
        this.appliesToDeposit = appliesToDeposit;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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

