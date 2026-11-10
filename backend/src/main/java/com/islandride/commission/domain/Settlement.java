package com.islandride.commission.domain;

import com.islandride.core.domain.BaseEntity;
import com.islandride.identityaccess.domain.User;
import com.islandride.partner.domain.Business;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "settlements")
public class Settlement extends BaseEntity {

    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    private String publicId = "stl_" + UUID.randomUUID().toString().replace("-", "");

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(name = "settlement_period_start", nullable = false)
    private LocalDate settlementPeriodStart;

    @Column(name = "settlement_period_end", nullable = false)
    private LocalDate settlementPeriodEnd;

    @Column(name = "total_gross_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalGrossAmount;

    @Column(name = "total_commission_deducted", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCommissionDeducted;

    @Column(name = "net_payout_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal netPayoutAmount;

    @Column(name = "bank_utr_reference", length = 50)
    private String bankUtrReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SettlementStatus status = SettlementStatus.INITIATED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "settlement_items",
        joinColumns = @JoinColumn(name = "settlement_id"),
        inverseJoinColumns = @JoinColumn(name = "commission_id")
    )
    private Set<Commission> items = new HashSet<>();

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

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public Business getBusiness() {
        return business;
    }

    public void setBusiness(Business business) {
        this.business = business;
    }

    public LocalDate getSettlementPeriodStart() {
        return settlementPeriodStart;
    }

    public void setSettlementPeriodStart(LocalDate settlementPeriodStart) {
        this.settlementPeriodStart = settlementPeriodStart;
    }

    public LocalDate getSettlementPeriodEnd() {
        return settlementPeriodEnd;
    }

    public void setSettlementPeriodEnd(LocalDate settlementPeriodEnd) {
        this.settlementPeriodEnd = settlementPeriodEnd;
    }

    public BigDecimal getTotalGrossAmount() {
        return totalGrossAmount;
    }

    public void setTotalGrossAmount(BigDecimal totalGrossAmount) {
        this.totalGrossAmount = totalGrossAmount;
    }

    public BigDecimal getTotalCommissionDeducted() {
        return totalCommissionDeducted;
    }

    public void setTotalCommissionDeducted(BigDecimal totalCommissionDeducted) {
        this.totalCommissionDeducted = totalCommissionDeducted;
    }

    public BigDecimal getNetPayoutAmount() {
        return netPayoutAmount;
    }

    public void setNetPayoutAmount(BigDecimal netPayoutAmount) {
        this.netPayoutAmount = netPayoutAmount;
    }

    public String getBankUtrReference() {
        return bankUtrReference;
    }

    public void setBankUtrReference(String bankUtrReference) {
        this.bankUtrReference = bankUtrReference;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public void setStatus(SettlementStatus status) {
        this.status = status;
    }

    public User getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(User processedBy) {
        this.processedBy = processedBy;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
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

    public Set<Commission> getItems() {
        return items;
    }

    public void setItems(Set<Commission> items) {
        this.items = items;
    }

    public void addCommission(Commission commission) {
        this.items.add(commission);
    }
}

